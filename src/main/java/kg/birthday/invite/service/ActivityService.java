package kg.birthday.invite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.activity.modules.GuestCertificatesModule;
import kg.birthday.invite.activity.registry.ActivityRegistry;
import kg.birthday.invite.dto.ActivityModuleInfo;
import kg.birthday.invite.dto.ActivityView;
import kg.birthday.invite.dto.GuestScore;
import kg.birthday.invite.entity.ActivityInstance;
import kg.birthday.invite.entity.ActivityResultEntity;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.enums.RsvpStatus;
import kg.birthday.invite.repository.ActivityInstanceRepository;
import kg.birthday.invite.repository.ActivityResultRepository;
import kg.birthday.invite.repository.EventRepository;
import kg.birthday.invite.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final SecureRandom CERTIFICATE_RANDOM = new SecureRandom();

    private final ActivityRegistry activityRegistry;
    private final ActivityInstanceRepository activityInstanceRepository;
    private final ActivityResultRepository activityResultRepository;
    private final EventRepository eventRepository;
    private final GuestRepository guestRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ActivityModuleInfo> getAvailableModules() {
        return activityRegistry.getModules().stream().map(this::toInfo).toList();
    }

    @Transactional(readOnly = true)
    public List<Guest> getAcceptedGuests(Long eventId) {
        return guestRepository.findAllByEventIdAndStatus(eventId, RsvpStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public List<ActivityInstance> getEnabledActivities(Long eventId) {
        return activityInstanceRepository.findAllByEventIdAndEnabledTrueOrderBySortOrderAsc(eventId);
    }

    @Transactional(readOnly = true)
    public List<ActivityView> getEnabledActivityViews(Long eventId) {
        return toViews(getEnabledActivities(eventId));
    }

    @Transactional(readOnly = true)
    public List<ActivityView> getDisabledActivityViews(Long eventId) {
        return toViews(activityInstanceRepository.findAllByEventIdAndEnabledFalseOrderBySortOrderAsc(eventId));
    }

    @Transactional(readOnly = true)
    public List<ActivityView> getAllActivityViews(Long eventId) {
        return toViews(activityInstanceRepository.findAllByEventIdOrderBySortOrderAsc(eventId));
    }

    @Transactional
    public ActivityInstance enableActivity(Long eventId, String slug, String displayName) {
        ActivityModule module = getModule(slug);
        Optional<ActivityInstance> existing = activityInstanceRepository.findFirstByEventIdAndModuleSlugAndEnabledTrue(eventId, slug);
        if (existing.isPresent()) {
            return existing.get();
        }
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        ActivityInstance instance = new ActivityInstance();
        instance.setEvent(event);
        instance.setModuleSlug(slug);
        instance.setDisplayName(displayName == null || displayName.isBlank() ? module.getDisplayName() : displayName.trim());
        instance.setConfig(new LinkedHashMap<>(module.getDefaultConfig()));
        instance.setEnabled(true);
        instance.setSortOrder(activityInstanceRepository.findAllByEventIdOrderBySortOrderAsc(eventId).size() + 1);
        return activityInstanceRepository.save(instance);
    }

    @Transactional
    public void disableActivity(Long instanceId) {
        ActivityInstance instance = getInstance(instanceId);
        instance.setEnabled(false);
        activityInstanceRepository.save(instance);
    }

    @Transactional
    public void disableActivity(Long eventId, Long instanceId) {
        ActivityInstance instance = getInstance(eventId, instanceId);
        instance.setEnabled(false);
        activityInstanceRepository.save(instance);
    }

    @Transactional
    public ActivityInstance updateActivityConfig(Long instanceId, Map<String, Object> config) {
        ActivityInstance instance = getInstance(instanceId);
        ActivityModule module = getModule(instance.getModuleSlug());
        List<String> errors = module.validateConfig(config);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        instance.setConfig(new LinkedHashMap<>(config));
        return activityInstanceRepository.save(instance);
    }

    @Transactional
    public ActivityInstance updateActivityConfigFromJson(Long instanceId, String configJson) {
        try {
            return updateActivityConfig(instanceId, objectMapper.readValue(configJson, MAP_TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON config", e);
        }
    }

    @Transactional
    public ActivityInstance updateActivityConfigFromJson(Long eventId, Long instanceId, String configJson) {
        getInstance(eventId, instanceId);
        return updateActivityConfigFromJson(instanceId, configJson);
    }

    @Transactional
    public ActivityInstance updateActivityConfigFromForm(Long instanceId, MultiValueMap<String, String> form) {
        ActivityInstance instance = getInstance(instanceId);
        ActivityModule module = getModule(instance.getModuleSlug());
        String displayName = first(form, "displayName");
        if (displayName != null) {
            instance.setDisplayName(displayName);
        }
        Map<String, Object> config = buildConfigFromForm(instance.getModuleSlug(), form);
        List<String> errors = module.validateConfig(config);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        instance.setConfig(new LinkedHashMap<>(config));
        return activityInstanceRepository.save(instance);
    }

    @Transactional
    public ActivityInstance updateActivityConfigFromForm(Long eventId, Long instanceId, MultiValueMap<String, String> form) {
        getInstance(eventId, instanceId);
        return updateActivityConfigFromForm(instanceId, form);
    }

    @Transactional
    public ActivityResult playActivity(Long instanceId, Long guestId, Map<String, Object> action) {
        ActivityInstance instance = getInstanceForUpdate(instanceId);
        if (!instance.isEnabled()) {
            return ActivityResult.error("Активность отключена.");
        }
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found: " + guestId));
        if (!guest.getEvent().getId().equals(instance.getEvent().getId())) {
            return ActivityResult.error("Гость не относится к этому событию.");
        }
        ActivityModule module = getModule(instance.getModuleSlug());
        List<ActivityResultEntity> previousResults = activityResultsForAction(instance, guestId);
        Map<String, Object> actionData = new LinkedHashMap<>(action == null ? Map.of() : action);
        if ("countdown-challenge".equals(instance.getModuleSlug())) {
            actionData.put("_eventDate", instance.getEvent().getDate().toString());
        }
        ActivityResult result = module.processAction(instance.getConfig(), actionData, previousResults);
        if (result.isSuccess()) {
            Guest scoreGuest = scoreGuest(result.getData(), guest);
            ActivityResultEntity entity = new ActivityResultEntity();
            entity.setActivityInstance(instance);
            entity.setGuest(scoreGuest);
            entity.setPoints(result.getPoints());
            entity.setResultData(new LinkedHashMap<>(result.getData()));
            ActivityResultEntity saved = activityResultRepository.save(entity);
            result.getData().put("resultId", saved.getId());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ActivityResultEntity> getResults(Long instanceId) {
        return activityResultRepository.findAllByActivityInstanceIdOrderByCreatedAtDesc(instanceId);
    }

    @Transactional(readOnly = true)
    public List<ActivityResultEntity> getResults(Long eventId, Long instanceId) {
        getInstance(eventId, instanceId);
        return getResults(instanceId);
    }

    @Transactional(readOnly = true)
    public List<ActivityResultEntity> getGuestResults(Long instanceId, Long guestId) {
        return activityResultRepository.findAllByActivityInstanceIdAndGuestIdOrderByCreatedAtDesc(instanceId, guestId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ActivityResultEntity>> getGuestResultsByActivity(Long eventId, Long guestId) {
        Map<Long, List<ActivityResultEntity>> resultsByActivity = new LinkedHashMap<>();
        for (ActivityInstance activity : getEnabledActivities(eventId)) {
            resultsByActivity.put(
                    activity.getId(),
                    activityResultRepository.findAllByActivityInstanceIdAndGuestIdOrderByCreatedAtDesc(activity.getId(), guestId)
            );
        }
        return resultsByActivity;
    }

    @Transactional(readOnly = true)
    public boolean hasGuestPlayed(Long instanceId, Long guestId) {
        return activityResultRepository.existsByActivityInstanceIdAndGuestId(instanceId, guestId);
    }

    @Transactional(readOnly = true)
    public List<ActivityResultEntity> getGuestCertificates(Long eventId, Long guestId) {
        return activityResultRepository.findAllByActivityInstance_Event_IdAndGuest_IdOrderByCreatedAtDesc(eventId, guestId).stream()
                .filter(ActivityService::isCertificateResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ActivityResultEntity> getGuestCertificate(Long eventId, Long guestId, Long resultId) {
        return activityResultRepository.findByIdAndActivityInstance_Event_IdAndGuest_Id(resultId, eventId, guestId)
                .filter(ActivityService::isCertificateResult);
    }

    @Transactional
    public ActivityResult awardCertificate(
            Long eventId,
            Long instanceId,
            Long guestId,
            String certificateTypeSlug,
            String customTitle,
            String customText
    ) {
        ActivityInstance instance = certificateInstance(eventId, instanceId);
        Guest guest = guestRepository.findByIdAndEventId(guestId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found for event: " + guestId));
        Map<String, Object> certificateType = certificateType(instance, certificateTypeSlug);
        Map<String, Object> data = certificateData(instance, guest, certificateType, customTitle, customText, "manual");
        ActivityResultEntity entity = new ActivityResultEntity();
        entity.setActivityInstance(instance);
        entity.setGuest(guest);
        entity.setPoints(0);
        entity.setResultData(data);
        ActivityResultEntity saved = activityResultRepository.save(entity);
        data.put("resultId", saved.getId());
        saved.setResultData(data);
        return ActivityResult.success("Сертификат выдан", 0, data);
    }

    @Transactional
    public List<ActivityResult> generateCertificates(Long eventId, Long instanceId, boolean overwriteExisting) {
        ActivityInstance instance = certificateInstance(eventId, instanceId);
        if (!bool(instance.getConfig().getOrDefault("autoGenerateEnabled", true))) {
            return List.of();
        }
        if (getEventLeaderboard(eventId).isEmpty()) {
            return List.of();
        }
        List<ActivityResult> generated = new ArrayList<>();
        for (Map<String, Object> certificateType : GuestCertificatesModule.certificateTypes(instance.getConfig())) {
            String source = text(certificateType.get("source"));
            String slug = text(certificateType.get("slug"));
            if (slug == null || source == null || "manual".equals(source)) {
                continue;
            }
            List<ActivityResultEntity> existing = certificateResults(instance.getId()).stream()
                    .filter(result -> slug.equals(text(result.getResultData().get("certificateTypeSlug"))))
                    .toList();
            if (!existing.isEmpty() && !overwriteExisting) {
                continue;
            }
            if (overwriteExisting) {
                existing.forEach(activityResultRepository::delete);
            }
            Guest winner = switch (source) {
                case "activity_leaderboard" -> activityLeaderboardGuest(eventId, map(certificateType.get("rule")));
                case "total_leaderboard" -> totalLeaderboardGuest(eventId, map(certificateType.get("rule")));
                default -> null;
            };
            if (winner != null) {
                generated.add(awardCertificate(eventId, instanceId, winner.getId(), slug, null, null));
            }
        }
        return generated;
    }

    @Transactional
    public void deleteCertificate(Long eventId, Long instanceId, Long resultId) {
        certificateInstance(eventId, instanceId);
        ActivityResultEntity result = activityResultRepository.findById(resultId)
                .filter(ActivityService::isCertificateResult)
                .filter(item -> Objects.equals(item.getActivityInstance().getId(), instanceId))
                .filter(item -> Objects.equals(item.getActivityInstance().getEvent().getId(), eventId))
                .orElseThrow(() -> new IllegalArgumentException("Certificate result not found: " + resultId));
        activityResultRepository.delete(result);
    }

    @Transactional(readOnly = true)
    public boolean hasPositiveActivityResults(Long eventId) {
        return activityResultRepository.findAllByActivityInstance_Event_Id(eventId).stream()
                .anyMatch(result -> result.getPoints() > 0);
    }

    @Transactional(readOnly = true)
    public List<GuestScore> getLeaderboard(Long eventId) {
        return getEventLeaderboard(eventId);
    }

    @Transactional(readOnly = true)
    public List<GuestScore> getEventLeaderboard(Long eventId) {
        List<ActivityResultEntity> results = activityResultRepository.findAllByActivityInstance_Event_Id(eventId);
        Map<Long, GuestScoreAccumulator> scores = new LinkedHashMap<>();
        for (ActivityResultEntity result : results) {
            if (result.getPoints() <= 0) {
                continue;
            }
            Guest guest = result.getGuest();
            String activityName = result.getActivityInstance().getDisplayName();
            GuestScoreAccumulator accumulator = scores.computeIfAbsent(guest.getId(), id ->
                    new GuestScoreAccumulator(guest.getId(), displayGuestName(guest)));
            accumulator.totalPoints += result.getPoints();
            accumulator.pointsByActivity.merge(activityName, result.getPoints(), Integer::sum);
        }
        return scores.values().stream()
                .map(acc -> new GuestScore(acc.guestId, acc.guestName, acc.totalPoints, acc.pointsByActivity))
                .sorted(Comparator.comparingInt(GuestScore::totalPoints).reversed())
                .toList();
    }

    private ActivityInstance certificateInstance(Long eventId, Long instanceId) {
        ActivityInstance instance = getInstance(eventId, instanceId);
        if (!"guest-certificates".equals(instance.getModuleSlug())) {
            throw new IllegalArgumentException("Activity is not guest-certificates: " + instanceId);
        }
        return instance;
    }

    private Map<String, Object> certificateType(ActivityInstance instance, String certificateTypeSlug) {
        String requestedSlug = text(certificateTypeSlug);
        return GuestCertificatesModule.certificateTypes(instance.getConfig()).stream()
                .filter(type -> Objects.equals(text(type.get("slug")), requestedSlug))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> custom = new LinkedHashMap<>();
                    custom.put("slug", requestedSlug == null ? "custom" : requestedSlug);
                    custom.put("title", requestedSlug == null ? "Сертификат гостя" : requestedSlug);
                    custom.put("description", "");
                    custom.put("source", "manual");
                    custom.put("rule", Map.of());
                    return custom;
                });
    }

    private Map<String, Object> certificateData(
            ActivityInstance instance,
            Guest guest,
            Map<String, Object> certificateType,
            String customTitle,
            String customText,
            String source
    ) {
        Event event = instance.getEvent();
        String certificateTitle = firstText(customTitle, textOrFallback(certificateType.get("title"), "Сертификат гостя"));
        String certificateDescription = firstText(customText, textOrFallback(certificateType.get("description"), ""));
        String guestName = displayGuestName(guest);
        String textTemplate = textOrFallback(instance.getConfig().get("certificateText"),
                "Настоящий сертификат подтверждает, что {guestName} получает звание «{certificateTitle}» на дне рождения {eventName}.");
        String certificateText = textTemplate
                .replace("{guestName}", guestName)
                .replace("{certificateTitle}", certificateTitle)
                .replace("{eventName}", event.getName());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "guest-certificate");
        data.put("action", "certificate_awarded");
        data.put("source", source);
        data.put("certificateTypeSlug", textOrFallback(certificateType.get("slug"), "custom"));
        data.put("certificateCode", certificateCode(event));
        data.put("guestId", guest.getId());
        data.put("guestName", guestName);
        data.put("certificateTitle", certificateTitle);
        data.put("certificateDescription", certificateDescription);
        data.put("certificateText", certificateText);
        data.put("template", textOrFallback(instance.getConfig().get("template"), "elegant-gold"));
        data.put("issuedAt", LocalDateTime.now().toString());
        data.put("eventName", event.getName());
        data.put("footerText", textOrFallback(instance.getConfig().get("footerText"), "Спасибо, что был(а) частью этого дня!"));
        return data;
    }

    private Guest activityLeaderboardGuest(Long eventId, Map<String, Object> rule) {
        String activitySlug = text(rule.get("activitySlug"));
        int rank = Math.max(1, number(rule.get("rank"), 1));
        if (activitySlug == null) {
            return null;
        }
        return activityResultRepository.findAllByActivityInstance_Event_Id(eventId).stream()
                .filter(result -> result.getPoints() > 0)
                .filter(result -> activitySlug.equals(result.getActivityInstance().getModuleSlug()))
                .collect(Collectors.groupingBy(result -> result.getGuest().getId(), Collectors.toList()))
                .values().stream()
                .map(results -> Map.entry(results.get(0).getGuest(), results.stream().mapToInt(ActivityResultEntity::getPoints).sum()))
                .sorted(Map.Entry.<Guest, Integer>comparingByValue().reversed())
                .skip(rank - 1L)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private Guest totalLeaderboardGuest(Long eventId, Map<String, Object> rule) {
        int rank = Math.max(1, number(rule.get("rank"), 1));
        return getEventLeaderboard(eventId).stream()
                .skip(rank - 1L)
                .findFirst()
                .flatMap(score -> guestRepository.findByIdAndEventId(score.guestId(), eventId))
                .orElse(null);
    }

    private List<ActivityResultEntity> certificateResults(Long instanceId) {
        return activityResultRepository.findAllByActivityInstanceIdOrderByCreatedAtDesc(instanceId).stream()
                .filter(ActivityService::isCertificateResult)
                .toList();
    }

    private static boolean isCertificateResult(ActivityResultEntity result) {
        return result != null
                && result.getResultData() != null
                && "guest-certificate".equals(String.valueOf(result.getResultData().get("type")));
    }

    private static String certificateCode(Event event) {
        int year = event.getDate() == null ? java.time.Year.now().getValue() : event.getDate().getYear();
        int suffix = CERTIFICATE_RANDOM.nextInt(1_000_000);
        return "CERT-" + year + "-" + String.format("%06d", suffix);
    }

    private List<ActivityView> toViews(List<ActivityInstance> instances) {
        return instances.stream().map(instance -> {
            ActivityModule module = getModule(instance.getModuleSlug());
            normalizeConfigForView(instance, module);
            List<ActivityResultEntity> results = getResults(instance.getId());
            normalizeResultsForView(instance, results);
            Map<Long, Integer> pointsByGuest = results.stream()
                    .collect(Collectors.groupingBy(result -> result.getGuest().getId(), Collectors.summingInt(ActivityResultEntity::getPoints)));
            String leader = pointsByGuest.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .flatMap(entry -> results.stream()
                            .map(ActivityResultEntity::getGuest)
                            .filter(guest -> Objects.equals(guest.getId(), entry.getKey()))
                            .findFirst())
                    .map(this::displayGuestName)
                    .orElse("—");
            return new ActivityView(
                    instance,
                    toInfo(module),
                    toPrettyJson(instance.getConfig()),
                    module.getGuestFragmentName(),
                    module.getAdminFragmentName(),
                    results,
                    pointsByGuest.size(),
                    leader
            );
        }).toList();
    }

    private void normalizeConfigForView(ActivityInstance instance, ActivityModule module) {
        if (!"photo-challenge".equals(instance.getModuleSlug())) {
            return;
        }
        if (instance.getDisplayName() == null
                || instance.getDisplayName().isBlank()
                || "Фото-челлендж".equals(instance.getDisplayName())) {
            instance.setDisplayName("Мем-челлендж");
        }
        Map<String, Object> config = instance.getConfig();
        if (config == null) {
            return;
        }
        if (config.get("memes") != null) {
            normalizePhotoChallengeTitle(config);
            normalizePhotoChallengeInstructions(config);
            return;
        }
        List<String> legacyChallenges = config.get("challenges") instanceof List<?> list
                ? list.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList()
                : List.of();
        if (legacyChallenges.isEmpty()) {
            return;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(module.getDefaultConfig());
        normalizePhotoChallengeTitle(normalized);
        normalizePhotoChallengeInstructions(normalized);
        normalized.put("showGallery", config.getOrDefault("showGallery", normalized.get("showGallery")));
        normalized.put("allowVoting", config.getOrDefault("allowVoting", normalized.get("allowVoting")));
        instance.setConfig(normalized);
    }

    private void normalizePhotoChallengeTitle(Map<String, Object> config) {
        Object title = config.get("title");
        if (title == null || String.valueOf(title).isBlank() || "Фото-челлендж".equals(String.valueOf(title))) {
            config.put("title", "Мем-челлендж");
        }
    }

    private void normalizePhotoChallengeInstructions(Map<String, Object> config) {
        Object instructions = config.get("instructions");
        if (instructions == null
                || String.valueOf(instructions).isBlank()
                || String.valueOf(instructions).contains("придумай подпись")) {
            config.put("instructions", "Крути рулетку: тебе выпадет мем-картинка, которую нужно повторить на фото.");
        }
    }

    @SuppressWarnings("unchecked")
    private void normalizeResultsForView(ActivityInstance instance, List<ActivityResultEntity> results) {
        if (!"photo-challenge".equals(instance.getModuleSlug()) || results == null) {
            return;
        }
        Object configuredMemes = instance.getConfig() == null ? null : instance.getConfig().get("memes");
        if (!(configuredMemes instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<Map<String, Object>> memes = list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
        for (ActivityResultEntity result : results) {
            Map<String, Object> data = result.getResultData();
            if (data == null || !isMemeResult(data)) {
                continue;
            }
            String imageUrl = text(data.get("memeImageUrl"));
            String memeId = text(data.get("memeId"));
            if (imageUrl != null && !memeIdIsLegacy(memeId)) {
                continue;
            }
            replacementMeme(data, memes).ifPresent(meme -> {
                Map<String, Object> normalized = new LinkedHashMap<>(data);
                normalized.put("memeId", textOrFallback(meme.get("id"), textOrFallback(data.get("memeId"), "meme")));
                normalized.put("memeName", textOrFallback(meme.get("name"), textOrFallback(data.get("memeName"), "Мем")));
                normalized.put("memeRegion", textOrFallback(meme.get("region"), textOrFallback(data.get("memeRegion"), "global")));
                normalized.put("memePrompt", textOrFallback(meme.get("prompt"), textOrFallback(data.get("memePrompt"), "Повтори позу, эмоцию или сцену с картинки")));
                normalized.put("memeImageUrl", textOrFallback(meme.get("imageUrl"), textOrFallback(data.get("memeImageUrl"), "")));
                normalized.put("memeAccent", textOrFallback(meme.get("accent"), textOrFallback(data.get("memeAccent"), "#ffd166")));
                normalized.put("memeEmoji", textOrFallback(meme.get("emoji"), textOrFallback(data.get("memeEmoji"), "🖼")));
                result.setResultData(normalized);
            });
        }
    }

    private Optional<Map<String, Object>> replacementMeme(Map<String, Object> data, List<Map<String, Object>> memes) {
        String memeId = text(data.get("memeId"));
        if (memeIdIsLegacy(memeId)) {
            Long legacyNumber = number(memeId.substring("legacy-".length()));
            int index = legacyNumber == null ? 0 : Math.floorMod(legacyNumber.intValue() - 1, memes.size());
            return Optional.of(memes.get(index));
        }
        return memes.stream()
                .filter(meme -> Objects.equals(text(meme.get("id")), memeId))
                .findFirst();
    }

    private boolean isMemeResult(Map<String, Object> data) {
        return "meme-challenge".equals(String.valueOf(data.get("type")))
                || "photo-challenge".equals(String.valueOf(data.get("legacyType")));
    }

    private boolean memeIdIsLegacy(String memeId) {
        return memeId != null && memeId.startsWith("legacy-");
    }

    private String textOrFallback(Object value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private ActivityModuleInfo toInfo(ActivityModule module) {
        return new ActivityModuleInfo(
                module.getSlug(),
                module.getDisplayName(),
                module.getDescription(),
                module.getIcon(),
                module.getGuestFragmentName(),
                module.getAdminFragmentName()
        );
    }

    private ActivityModule getModule(String slug) {
        return activityRegistry.getBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Activity module not found: " + slug));
    }

    private ActivityInstance getInstance(Long instanceId) {
        return activityInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Activity instance not found: " + instanceId));
    }

    private ActivityInstance getInstanceForUpdate(Long instanceId) {
        return activityInstanceRepository.findByIdForUpdate(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Activity instance not found: " + instanceId));
    }

    private ActivityInstance getInstance(Long eventId, Long instanceId) {
        return activityInstanceRepository.findByIdAndEventId(instanceId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Activity instance not found for event: " + instanceId));
    }

    private List<ActivityResultEntity> activityResultsForAction(ActivityInstance instance, Long guestId) {
        if ("photo-challenge".equals(instance.getModuleSlug()) || "truth-or-dare".equals(instance.getModuleSlug())) {
            return activityResultRepository.findAllByActivityInstanceIdOrderByCreatedAtDesc(instance.getId());
        }
        return activityResultRepository.findAllByActivityInstanceIdAndGuestIdOrderByCreatedAtDesc(instance.getId(), guestId);
    }

    private Guest scoreGuest(Map<String, Object> data, Guest fallback) {
        Long scoreGuestId = number(data.get("scoreGuestId"));
        if (scoreGuestId == null || Objects.equals(scoreGuestId, fallback.getId())) {
            return fallback;
        }
        return guestRepository.findById(scoreGuestId)
                .filter(guest -> Objects.equals(guest.getEvent().getId(), fallback.getEvent().getId()))
                .orElse(fallback);
    }

    private Long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String toPrettyJson(Map<String, Object> config) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String displayGuestName(Guest guest) {
        if (guest.getName() != null && !guest.getName().isBlank()) {
            return guest.getName();
        }
        return guest.getLabel();
    }

    private Map<String, Object> buildConfigFromForm(String moduleSlug, MultiValueMap<String, String> form) {
        return switch (moduleSlug) {
            case "wheel" -> wheelConfig(form);
            case "quiz" -> quizConfig(form);
            case "photo-challenge" -> photoChallengeConfig(form);
            case "truth-or-dare" -> truthOrDareConfig(form);
            case "guess-guest" -> guessGuestConfig(form);
            case "countdown-challenge" -> countdownChallengeConfig(form);
            case "guest-certificates" -> guestCertificatesConfig(form);
            default -> throw new IllegalArgumentException("Activity module not found: " + moduleSlug);
        };
    }

    private Map<String, Object> wheelConfig(MultiValueMap<String, String> form) {
        List<String> texts = values(form, "segmentTexts");
        List<String> colors = values(form, "segmentColors");
        List<String> weights = values(form, "segmentWeights");
        List<Map<String, Object>> segments = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = trimToNull(texts.get(i));
            if (text == null) {
                continue;
            }
            Map<String, Object> segment = new LinkedHashMap<>();
            segment.put("text", text);
            segment.put("color", color(valueAt(colors, i), colorForIndex(i)));
            segment.put("weight", Math.max(0, number(valueAt(weights, i), 1)));
            segments.add(segment);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", firstOrDefault(form, "title", "Крути колесо!"));
        config.put("mode", firstOrDefault(form, "mode", "prizes"));
        config.put("segments", segments);
        config.put("spinPerGuest", Math.max(0, number(firstRaw(form, "spinPerGuest"), 1)));
        config.put("showHistory", checked(form, "showHistory"));
        return config;
    }

    private Map<String, Object> quizConfig(MultiValueMap<String, String> form) {
        List<String> texts = values(form, "questionTexts");
        List<String> types = values(form, "questionTypes");
        List<String> options = values(form, "questionOptions");
        List<String> correctAnswers = values(form, "questionCorrectAnswers");
        List<String> points = values(form, "questionPoints");
        List<Map<String, Object>> questions = new java.util.ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = trimToNull(texts.get(i));
            if (text == null) {
                continue;
            }
            String type = "text_input".equals(valueAt(types, i)) ? "text_input" : "multiple_choice";
            List<String> optionList = "multiple_choice".equals(type) ? splitLines(valueAt(options, i)) : List.of();
            Object correctAnswer = "multiple_choice".equals(type)
                    ? multipleChoiceAnswer(valueAt(correctAnswers, i), optionList)
                    : firstText(valueAt(correctAnswers, i), "");
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("id", "q" + (questions.size() + 1));
            question.put("text", text);
            question.put("type", type);
            question.put("options", optionList);
            question.put("correctAnswer", correctAnswer);
            question.put("points", Math.max(0, number(valueAt(points, i), 10)));
            questions.add(question);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", firstOrDefault(form, "title", "Насколько хорошо ты меня знаешь?"));
        config.put("questions", questions);
        config.put("showCorrectAnswers", checked(form, "showCorrectAnswers"));
        config.put("showLeaderboard", checked(form, "showLeaderboard"));
        config.put("allowRetry", checked(form, "allowRetry"));
        config.put("timeLimitSeconds", Math.max(0, number(firstRaw(form, "timeLimitSeconds"), 0)));
        return config;
    }

    private Map<String, Object> photoChallengeConfig(MultiValueMap<String, String> form) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", firstOrDefault(form, "title", "Мем-челлендж"));
        config.put("instructions", firstOrDefault(form, "instructions", "Крути рулетку: тебе выпадет мем-картинка, которую нужно повторить на фото."));
        config.put("memes", memeTemplates(form));
        config.put("showGallery", checked(form, "showGallery"));
        config.put("allowVoting", checked(form, "allowVoting"));
        config.put("pointsForCaption", Math.max(0, number(firstRaw(form, "pointsForCaption"), 5)));
        config.put("pointsForVote", Math.max(0, number(firstRaw(form, "pointsForVote"), 1)));
        return config;
    }

    private List<Map<String, Object>> memeTemplates(MultiValueMap<String, String> form) {
        List<String> ids = values(form, "memeIds");
        List<String> names = values(form, "memeNames");
        List<String> regions = values(form, "memeRegions");
        List<String> imageUrls = values(form, "memeImageUrls");
        List<String> prompts = values(form, "memePrompts");
        List<String> accents = values(form, "memeAccents");
        List<String> emojis = values(form, "memeEmojis");
        List<Map<String, Object>> memes = new java.util.ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = trimToNull(names.get(i));
            if (name == null) {
                continue;
            }
            Map<String, Object> meme = new LinkedHashMap<>();
            meme.put("id", firstText(valueAt(ids, i), slug(name)));
            meme.put("name", name);
            meme.put("region", firstText(valueAt(regions, i), "global"));
            meme.put("imageUrl", firstText(valueAt(imageUrls, i), ""));
            meme.put("prompt", firstText(valueAt(prompts, i), "Повтори позу, эмоцию или сцену с картинки"));
            meme.put("accent", color(valueAt(accents, i), colorForIndex(i)));
            meme.put("emoji", firstText(valueAt(emojis, i), "😂"));
            memes.add(meme);
        }
        return memes;
    }

    private Map<String, Object> truthOrDareConfig(MultiValueMap<String, String> form) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", firstOrDefault(form, "title", "Правда или Действие"));
        config.put("truths", values(form, "truthTexts").stream()
                .map(ActivityService::trimToNull)
                .filter(Objects::nonNull)
                .toList());
        config.put("dares", values(form, "dareTexts").stream()
                .map(ActivityService::trimToNull)
                .filter(Objects::nonNull)
                .toList());
        config.put("allowGuestAdd", checked(form, "allowGuestAdd"));
        return config;
    }

    private Map<String, Object> guessGuestConfig(MultiValueMap<String, String> form) {
        List<String> ids = values(form, "guessRoundIds");
        List<String> clues = values(form, "guessClues");
        List<String> answerIds = values(form, "guessAnswerGuestIds");
        List<String> optionIds = values(form, "guessOptionGuestIds");
        List<String> points = values(form, "guessPoints");
        List<Map<String, Object>> rounds = new ArrayList<>();
        for (int i = 0; i < clues.size(); i++) {
            String clue = trimToNull(clues.get(i));
            if (clue == null) {
                continue;
            }
            Long answerGuestId = number(valueAt(answerIds, i));
            List<Long> options = splitIds(valueAt(optionIds, i));
            if (answerGuestId != null && !options.contains(answerGuestId)) {
                options = new ArrayList<>(options);
                options.add(answerGuestId);
            }
            Map<Long, String> names = publicGuestNames(options);
            Map<String, Object> round = new LinkedHashMap<>();
            round.put("id", firstText(valueAt(ids, i), "r" + (rounds.size() + 1)));
            round.put("clue", clue);
            round.put("answerGuestId", answerGuestId);
            round.put("answerGuestName", answerGuestId == null ? "" : names.getOrDefault(answerGuestId, "Гость " + answerGuestId));
            round.put("optionGuestIds", options);
            round.put("optionGuestNames", names.entrySet().stream()
                    .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new)));
            round.put("points", Math.max(0, number(valueAt(points, i), 10)));
            rounds.add(round);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", firstOrDefault(form, "title", "Угадай гостя"));
        config.put("description", firstOrDefault(form, "description", "Прочитай подсказку и выбери, о ком идёт речь."));
        config.put("rounds", rounds);
        config.put("optionsCount", Math.max(2, number(firstRaw(form, "optionsCount"), 4)));
        config.put("shuffleOptions", checked(form, "shuffleOptions"));
        config.put("showCorrectAnswer", checked(form, "showCorrectAnswer"));
        config.put("showLeaderboard", checked(form, "showLeaderboard"));
        config.put("allowRetry", checked(form, "allowRetry"));
        config.put("useOnlyAcceptedGuests", checked(form, "useOnlyAcceptedGuests"));
        return config;
    }

    private Map<String, Object> countdownChallengeConfig(MultiValueMap<String, String> form) {
        List<String> ids = values(form, "countdownTaskIds");
        List<String> offsets = values(form, "countdownTaskOffsets");
        List<String> titles = values(form, "countdownTaskTitles");
        List<String> descriptions = values(form, "countdownTaskDescriptions");
        List<String> types = values(form, "countdownTaskTypes");
        List<String> options = values(form, "countdownTaskOptions");
        List<String> required = values(form, "countdownTaskRequired");
        List<String> codes = values(form, "countdownTaskCodes");
        List<String> points = values(form, "countdownTaskPoints");
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            String title = trimToNull(titles.get(i));
            String description = trimToNull(valueAt(descriptions, i));
            if (title == null && description == null) {
                continue;
            }
            String type = firstText(valueAt(types, i), "text");
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("id", firstText(valueAt(ids, i), "day-" + Math.max(0, number(valueAt(offsets, i), tasks.size() + 1))));
            task.put("dayOffset", Math.max(0, number(valueAt(offsets, i), tasks.size() + 1)));
            task.put("title", title == null ? "Задание" : title);
            task.put("description", description == null ? "Описание задания" : description);
            task.put("type", type);
            task.put("options", splitLines(valueAt(options, i)));
            task.put("required", !"false".equals(valueAt(required, i)));
            task.put("points", Math.max(0, number(valueAt(points, i), 5)));
            String correctCode = trimToNull(valueAt(codes, i));
            if (correctCode != null) {
                task.put("correctCode", correctCode);
            }
            tasks.add(task);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", firstOrDefault(form, "title", "Обратный отсчёт до праздника"));
        config.put("description", firstOrDefault(form, "description", "Каждый день открывается новое задание."));
        config.put("timezone", firstOrDefault(form, "timezone", "Asia/Bishkek"));
        config.put("unlockMode", firstOrDefault(form, "unlockMode", "daily"));
        config.put("missedDaysPolicy", firstOrDefault(form, "missedDaysPolicy", "allow_previous"));
        config.put("showCountdownTimer", checked(form, "showCountdownTimer"));
        config.put("showProgress", checked(form, "showProgress"));
        config.put("allowLateCompletion", checked(form, "allowLateCompletion"));
        config.put("tasks", tasks);
        return config;
    }

    private Map<String, Object> guestCertificatesConfig(MultiValueMap<String, String> form) {
        List<String> slugs = values(form, "certificateSlugs");
        List<String> titles = values(form, "certificateTitles");
        List<String> descriptions = values(form, "certificateDescriptions");
        List<String> sources = values(form, "certificateSources");
        List<String> activitySlugs = values(form, "certificateActivitySlugs");
        List<String> ranks = values(form, "certificateRanks");
        List<Map<String, Object>> certificateTypes = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            String title = trimToNull(titles.get(i));
            if (title == null) {
                continue;
            }
            String source = firstText(valueAt(sources, i), "manual");
            Map<String, Object> rule = new LinkedHashMap<>();
            if ("activity_leaderboard".equals(source)) {
                rule.put("activitySlug", firstText(valueAt(activitySlugs, i), "quiz"));
                rule.put("rank", Math.max(1, number(valueAt(ranks, i), 1)));
            } else if ("total_leaderboard".equals(source)) {
                rule.put("rank", Math.max(1, number(valueAt(ranks, i), 1)));
            }
            Map<String, Object> type = new LinkedHashMap<>();
            type.put("slug", firstText(valueAt(slugs, i), slug(title)));
            type.put("title", title);
            type.put("description", firstText(valueAt(descriptions, i), ""));
            type.put("source", source);
            type.put("rule", rule);
            certificateTypes.add(type);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", firstOrDefault(form, "title", "Сертификаты гостей"));
        config.put("description", firstOrDefault(form, "description", "Памятные награды для гостей праздника."));
        config.put("template", firstOrDefault(form, "template", "elegant-gold"));
        config.put("allowGuestDownload", checked(form, "allowGuestDownload"));
        config.put("allowGuestShare", checked(form, "allowGuestShare"));
        config.put("showOnGuestPage", checked(form, "showOnGuestPage"));
        config.put("autoGenerateEnabled", checked(form, "autoGenerateEnabled"));
        config.put("certificateTypes", certificateTypes);
        config.put("certificateText", firstOrDefault(form, "certificateText",
                "Настоящий сертификат подтверждает, что {guestName} получает звание «{certificateTitle}» на дне рождения {eventName}."));
        config.put("footerText", firstOrDefault(form, "footerText", "Спасибо, что был(а) частью этого дня!"));
        return config;
    }

    private List<Long> splitIds(String value) {
        String text = value == null ? "" : value;
        return java.util.Arrays.stream(text.split("[,;\\s]+"))
                .map(ActivityService::trimToNull)
                .filter(Objects::nonNull)
                .map(this::number)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Map<Long, String> publicGuestNames(List<Long> guestIds) {
        if (guestIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = guestRepository.findAllById(guestIds).stream()
                .collect(Collectors.toMap(Guest::getId, this::publicGuestName, (left, right) -> left, LinkedHashMap::new));
        Map<Long, String> ordered = new LinkedHashMap<>();
        for (Long guestId : guestIds) {
            ordered.put(guestId, names.getOrDefault(guestId, "Гость " + guestId));
        }
        return ordered;
    }

    private String publicGuestName(Guest guest) {
        String name = text(guest.getName());
        return name == null ? "Гость " + guest.getId() : name;
    }

    private static String firstRaw(MultiValueMap<String, String> form, String key) {
        return form.getFirst(key);
    }

    private static String first(MultiValueMap<String, String> form, String key) {
        return trimToNull(firstRaw(form, key));
    }

    private static String firstOrDefault(MultiValueMap<String, String> form, String key, String fallback) {
        String value = first(form, key);
        return value == null ? fallback : value;
    }

    private static String firstText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static List<String> values(MultiValueMap<String, String> form, String key) {
        List<String> values = form.get(key);
        return values == null ? List.of() : values;
    }

    private static String valueAt(List<String> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    private static boolean checked(MultiValueMap<String, String> form, String key) {
        return form.containsKey(key);
    }

    private static List<String> splitLines(String value) {
        String text = value == null ? "" : value;
        return java.util.Arrays.stream(text.split("\\R|;"))
                .map(ActivityService::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Object multipleChoiceAnswer(String value, List<String> options) {
        String answer = trimToNull(value);
        if (answer == null) {
            return 0;
        }
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).equalsIgnoreCase(answer)) {
                return i;
            }
        }
        int parsed = number(answer, 1);
        if (parsed == 0) {
            return 0;
        }
        return Math.max(0, parsed - 1);
    }

    private static String color(String value, String fallback) {
        String color = trimToNull(value);
        return color != null && color.matches("#[0-9a-fA-F]{6}") ? color : fallback;
    }

    private static String colorForIndex(int index) {
        return List.of("#C9A84C", "#7D9B76", "#B8704A", "#C07080", "#6B8EAE", "#9B7DC0")
                .get(index % 6);
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String slug(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return "meme";
        }
        String slug = text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9а-яё]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "meme" : slug;
    }

    private static class GuestScoreAccumulator {
        private final Long guestId;
        private final String guestName;
        private int totalPoints;
        private final Map<String, Integer> pointsByActivity = new LinkedHashMap<>();

        private GuestScoreAccumulator(Long guestId, String guestName) {
            this.guestId = guestId;
            this.guestName = guestName;
        }
    }
}
