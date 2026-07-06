package kg.birthday.invite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.activity.registry.ActivityRegistry;
import kg.birthday.invite.dto.ActivityModuleInfo;
import kg.birthday.invite.dto.ActivityView;
import kg.birthday.invite.dto.GuestScore;
import kg.birthday.invite.entity.ActivityInstance;
import kg.birthday.invite.entity.ActivityResultEntity;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.repository.ActivityInstanceRepository;
import kg.birthday.invite.repository.ActivityResultRepository;
import kg.birthday.invite.repository.EventRepository;
import kg.birthday.invite.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

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
        ActivityResult result = module.processAction(instance.getConfig(), action == null ? Map.of() : action, previousResults);
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
    public List<GuestScore> getLeaderboard(Long eventId) {
        return getEventLeaderboard(eventId);
    }

    @Transactional(readOnly = true)
    public List<GuestScore> getEventLeaderboard(Long eventId) {
        List<ActivityResultEntity> results = activityResultRepository.findAllByActivityInstance_Event_Id(eventId);
        Map<Long, GuestScoreAccumulator> scores = new LinkedHashMap<>();
        for (ActivityResultEntity result : results) {
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
