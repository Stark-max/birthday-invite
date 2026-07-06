package kg.birthday.invite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.activity.modules.CountdownChallengeModule;
import kg.birthday.invite.activity.modules.GuestCertificatesModule;
import kg.birthday.invite.activity.modules.GuessGuestModule;
import kg.birthday.invite.activity.modules.PhotoChallengeModule;
import kg.birthday.invite.activity.modules.QuizModule;
import kg.birthday.invite.activity.modules.TruthOrDareModule;
import kg.birthday.invite.activity.modules.WheelOfFortuneModule;
import kg.birthday.invite.activity.registry.ActivityRegistry;
import kg.birthday.invite.entity.ActivityInstance;
import kg.birthday.invite.entity.ActivityResultEntity;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.repository.ActivityInstanceRepository;
import kg.birthday.invite.repository.ActivityResultRepository;
import kg.birthday.invite.repository.EventRepository;
import kg.birthday.invite.repository.GuestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityServiceFormConfigTest {

    ActivityInstanceRepository activityInstanceRepository;
    ActivityResultRepository activityResultRepository;
    GuestRepository guestRepository;
    ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityInstanceRepository = mock(ActivityInstanceRepository.class);
        activityResultRepository = mock(ActivityResultRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        guestRepository = mock(GuestRepository.class);
        ActivityRegistry registry = new ActivityRegistry(List.of(
                new WheelOfFortuneModule(),
                new QuizModule(),
                new PhotoChallengeModule(),
                new TruthOrDareModule(),
                new GuessGuestModule(),
                new CountdownChallengeModule(),
                new GuestCertificatesModule()
        ));
        activityService = new ActivityService(
                registry,
                activityInstanceRepository,
                activityResultRepository,
                eventRepository,
                guestRepository,
                new ObjectMapper()
        );
        when(activityInstanceRepository.save(any(ActivityInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityResultRepository.save(any(ActivityResultEntity.class))).thenAnswer(invocation -> {
            ActivityResultEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(99L);
            }
            return entity;
        });
    }

    @Test
    void wheelFormBuildsWeightedSegmentsConfig() {
        ActivityInstance instance = instance("wheel");
        when(activityInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("displayName", "Колесо");
        form.add("title", "Крути");
        form.add("spinPerGuest", "2");
        form.add("showHistory", "true");
        form.add("segmentTexts", "Тост");
        form.add("segmentColors", "#C9A84C");
        form.add("segmentWeights", "3");
        form.add("segmentTexts", "Танец");
        form.add("segmentColors", "#7D9B76");
        form.add("segmentWeights", "1");

        ActivityInstance updated = activityService.updateActivityConfigFromForm(1L, form);

        assertThat(updated.getDisplayName()).isEqualTo("Колесо");
        assertThat(updated.getConfig()).containsEntry("title", "Крути");
        assertThat(updated.getConfig()).containsEntry("spinPerGuest", 2);
        assertThat((List<?>) updated.getConfig().get("segments")).hasSize(2);
    }

    @Test
    void quizFormBuildsQuestionConfig() {
        ActivityInstance instance = instance("quiz");
        when(activityInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("displayName", "Викторина");
        form.add("title", "Вопросы");
        form.add("showCorrectAnswers", "true");
        form.add("showLeaderboard", "true");
        form.add("questionTexts", "Любимый цвет?");
        form.add("questionTypes", "multiple_choice");
        form.add("questionOptions", "Синий\nЗеленый");
        form.add("questionCorrectAnswers", "2");
        form.add("questionPoints", "7");

        ActivityInstance updated = activityService.updateActivityConfigFromForm(1L, form);

        List<?> questions = (List<?>) updated.getConfig().get("questions");
        assertThat(questions).hasSize(1);
        Map<?, ?> question = (Map<?, ?>) questions.get(0);
        assertThat(question.get("text")).isEqualTo("Любимый цвет?");
        assertThat(question.get("correctAnswer")).isEqualTo(1);
        assertThat(question.get("points")).isEqualTo(7);
    }

    @Test
    void photoChallengeFormBuildsMemeConfig() {
        ActivityInstance instance = instance("photo-challenge");
        when(activityInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("title", "Мемы");
        form.add("instructions", "Повтори картинку");
        form.add("showGallery", "true");
        form.add("allowVoting", "true");
        form.add("pointsForCaption", "8");
        form.add("pointsForVote", "2");
        form.add("memeIds", "drake");
        form.add("memeNames", "Drake Hotline Bling");
        form.add("memeRegions", "US");
        form.add("memeImageUrls", "");
        form.add("memePrompts", "Выбор гостя");
        form.add("memeAccents", "#ffd166");
        form.add("memeEmojis", "🎧");

        ActivityInstance updated = activityService.updateActivityConfigFromForm(1L, form);

        assertThat(updated.getConfig()).containsEntry("title", "Мемы");
        assertThat(updated.getConfig()).containsEntry("instructions", "Повтори картинку");
        assertThat(updated.getConfig()).containsEntry("showGallery", true);
        assertThat(updated.getConfig()).containsEntry("allowVoting", true);
        assertThat(updated.getConfig()).containsEntry("pointsForCaption", 8);
        assertThat(updated.getConfig()).containsEntry("pointsForVote", 2);
        List<?> memes = (List<?>) updated.getConfig().get("memes");
        assertThat(memes).hasSize(1);
        Map<?, ?> meme = (Map<?, ?>) memes.get(0);
        assertThat(meme.get("id")).isEqualTo("drake");
        assertThat(meme.get("name")).isEqualTo("Drake Hotline Bling");
    }

    @Test
    void truthOrDareFormBuildsCardListsConfig() {
        ActivityInstance instance = instance("truth-or-dare");
        when(activityInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("title", "Карточки");
        form.add("allowGuestAdd", "true");
        form.add("truthTexts", "Правда?");
        form.add("dareTexts", "Действие!");

        ActivityInstance updated = activityService.updateActivityConfigFromForm(1L, form);

        assertThat(updated.getConfig()).containsEntry("title", "Карточки");
        assertThat(updated.getConfig()).containsEntry("allowGuestAdd", true);
        assertThat(updated.getConfig().get("truths")).isEqualTo(List.of("Правда?"));
        assertThat(updated.getConfig().get("dares")).isEqualTo(List.of("Действие!"));
    }

    @Test
    void guessGuestFormBuildsRoundsWithPublicGuestNames() {
        ActivityInstance instance = instance("guess-guest");
        when(activityInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        Guest guest = new Guest();
        guest.setId(12L);
        guest.setName("Айбек");
        when(guestRepository.findAllById(any())).thenReturn(List.of(guest));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("title", "Угадай");
        form.add("description", "Подсказки");
        form.add("optionsCount", "2");
        form.add("shuffleOptions", "true");
        form.add("showCorrectAnswer", "true");
        form.add("showLeaderboard", "true");
        form.add("useOnlyAcceptedGuests", "true");
        form.add("guessRoundIds", "r1");
        form.add("guessClues", "Знает именинника со школы");
        form.add("guessAnswerGuestIds", "12");
        form.add("guessOptionGuestIds", "12");
        form.add("guessPoints", "9");

        ActivityInstance updated = activityService.updateActivityConfigFromForm(1L, form);

        List<?> rounds = (List<?>) updated.getConfig().get("rounds");
        assertThat(rounds).hasSize(1);
        Map<?, ?> round = (Map<?, ?>) rounds.get(0);
        assertThat(round.get("answerGuestId")).isEqualTo(12L);
        assertThat(round.get("answerGuestName")).isEqualTo("Айбек");
        assertThat(round.get("points")).isEqualTo(9);
    }

    @Test
    void countdownFormBuildsTaskConfig() {
        ActivityInstance instance = instance("countdown-challenge");
        when(activityInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("title", "До праздника");
        form.add("description", "Каждый день");
        form.add("timezone", "Asia/Bishkek");
        form.add("unlockMode", "daily");
        form.add("missedDaysPolicy", "allow_previous");
        form.add("showCountdownTimer", "true");
        form.add("showProgress", "true");
        form.add("allowLateCompletion", "true");
        form.add("countdownTaskIds", "day-1");
        form.add("countdownTaskOffsets", "1");
        form.add("countdownTaskTitles", "1 день");
        form.add("countdownTaskDescriptions", "Подтверди готовность");
        form.add("countdownTaskTypes", "checkbox");
        form.add("countdownTaskOptions", "");
        form.add("countdownTaskRequired", "true");
        form.add("countdownTaskCodes", "");
        form.add("countdownTaskPoints", "6");

        ActivityInstance updated = activityService.updateActivityConfigFromForm(1L, form);

        List<?> tasks = (List<?>) updated.getConfig().get("tasks");
        assertThat(tasks).hasSize(1);
        Map<?, ?> task = (Map<?, ?>) tasks.get(0);
        assertThat(task.get("id")).isEqualTo("day-1");
        assertThat(task.get("type")).isEqualTo("checkbox");
        assertThat(task.get("points")).isEqualTo(6);
    }

    @Test
    void guestCertificatesFormBuildsCertificateTypes() {
        ActivityInstance instance = instance("guest-certificates");
        when(activityInstanceRepository.findById(1L)).thenReturn(Optional.of(instance));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("title", "Сертификаты");
        form.add("description", "Награды");
        form.add("template", "elegant-gold");
        form.add("allowGuestDownload", "true");
        form.add("allowGuestShare", "true");
        form.add("showOnGuestPage", "true");
        form.add("autoGenerateEnabled", "true");
        form.add("certificateText", "{guestName} получает «{certificateTitle}»");
        form.add("footerText", "Спасибо");
        form.add("certificateSlugs", "best");
        form.add("certificateTitles", "Лучший гость");
        form.add("certificateDescriptions", "За активность");
        form.add("certificateSources", "total_leaderboard");
        form.add("certificateActivitySlugs", "");
        form.add("certificateRanks", "1");

        ActivityInstance updated = activityService.updateActivityConfigFromForm(1L, form);

        List<?> types = (List<?>) updated.getConfig().get("certificateTypes");
        assertThat(types).hasSize(1);
        Map<?, ?> type = (Map<?, ?>) types.get(0);
        assertThat(type.get("slug")).isEqualTo("best");
        assertThat(type.get("source")).isEqualTo("total_leaderboard");
    }

    @Test
    void eventLeaderboardIgnoresZeroPointCertificateResults() {
        ActivityInstance quiz = instance("quiz");
        quiz.setDisplayName("Викторина");
        ActivityInstance certificates = instance("guest-certificates");
        certificates.setDisplayName("Сертификаты");
        Guest guest = new Guest();
        guest.setId(3L);
        guest.setName("Алина");
        ActivityResultEntity points = result(quiz, guest, 5, Map.of("type", "quiz"));
        ActivityResultEntity certificate = result(certificates, guest, 0, Map.of("type", "guest-certificate"));
        when(activityResultRepository.findAllByActivityInstance_Event_Id(10L)).thenReturn(List.of(certificate, points));

        List<kg.birthday.invite.dto.GuestScore> leaderboard = activityService.getEventLeaderboard(10L);

        assertThat(leaderboard).hasSize(1);
        assertThat(leaderboard.get(0).totalPoints()).isEqualTo(5);
        assertThat(leaderboard.get(0).pointsByActivity()).containsOnlyKeys("Викторина");
    }

    @Test
    void awardCertificateCreatesGuestCertificateResult() {
        Event event = event();
        ActivityInstance certificates = certificateInstance(event);
        Guest guest = guest(event, 3L, "Alina");
        when(activityInstanceRepository.findByIdAndEventId(7L, 10L)).thenReturn(Optional.of(certificates));
        when(guestRepository.findByIdAndEventId(3L, 10L)).thenReturn(Optional.of(guest));

        ActivityResult result = activityService.awardCertificate(10L, 7L, 3L, "best_dancer", null, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("type", "guest-certificate");
        assertThat(result.getData()).containsEntry("guestId", 3L);
        assertThat(result.getData()).containsEntry("resultId", 99L);
        org.mockito.ArgumentCaptor<ActivityResultEntity> saved = org.mockito.ArgumentCaptor.forClass(ActivityResultEntity.class);
        verify(activityResultRepository).save(saved.capture());
        assertThat(saved.getValue().getGuest()).isSameAs(guest);
        assertThat(saved.getValue().getActivityInstance()).isSameAs(certificates);
        assertThat(saved.getValue().getResultData()).containsEntry("type", "guest-certificate");
    }

    @Test
    void getGuestCertificatesReturnsOnlyCertificateResultsForCurrentGuestQuery() {
        Event event = event();
        ActivityInstance certificates = certificateInstance(event);
        Guest guest = guest(event, 3L, "Alina");
        ActivityResultEntity certificate = result(certificates, guest, 0, Map.of("type", "guest-certificate"));
        ActivityResultEntity score = result(certificates, guest, 5, Map.of("type", "quiz"));
        when(activityResultRepository.findAllByActivityInstance_Event_IdAndGuest_IdOrderByCreatedAtDesc(10L, 3L))
                .thenReturn(List.of(score, certificate));

        List<ActivityResultEntity> certificatesForGuest = activityService.getGuestCertificates(10L, 3L);

        assertThat(certificatesForGuest).containsExactly(certificate);
        verify(activityResultRepository).findAllByActivityInstance_Event_IdAndGuest_IdOrderByCreatedAtDesc(10L, 3L);
    }

    @Test
    void getGuestCertificateDoesNotReturnCertificateForAnotherGuest() {
        when(activityResultRepository.findByIdAndActivityInstance_Event_IdAndGuest_Id(99L, 10L, 4L))
                .thenReturn(Optional.empty());

        Optional<ActivityResultEntity> certificate = activityService.getGuestCertificate(10L, 4L, 99L);

        assertThat(certificate).isEmpty();
    }

    @Test
    void generateCertificatesDoesNotDuplicateExistingTypeWithoutOverwrite() {
        Event event = event();
        ActivityInstance certificates = certificateInstance(event);
        certificates.setConfig(certificateConfig("most_active_guest", "total_leaderboard", Map.of("rank", 1)));
        Guest guest = guest(event, 3L, "Alina");
        ActivityInstance quiz = instance("quiz");
        quiz.setDisplayName("Quiz");
        ActivityResultEntity score = result(quiz, guest, 10, Map.of("type", "quiz"));
        ActivityResultEntity existingCertificate = result(certificates, guest, 0, Map.of(
                "type", "guest-certificate",
                "certificateTypeSlug", "most_active_guest"
        ));
        when(activityInstanceRepository.findByIdAndEventId(7L, 10L)).thenReturn(Optional.of(certificates));
        when(activityResultRepository.findAllByActivityInstance_Event_Id(10L)).thenReturn(List.of(score));
        when(activityResultRepository.findAllByActivityInstanceIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(existingCertificate));

        List<ActivityResult> generated = activityService.generateCertificates(10L, 7L, false);

        assertThat(generated).isEmpty();
        verify(activityResultRepository, never()).save(any(ActivityResultEntity.class));
    }

    @Test
    void disabledActivityViewsUseDisabledRepositoryRows() {
        ActivityInstance disabled = instance("wheel");
        disabled.setEnabled(false);
        disabled.setConfig(new java.util.LinkedHashMap<>(Map.of(
                "title", "Старое",
                "mode", "prizes",
                "segments", List.of(Map.of("text", "Архив", "color", "#C9A84C", "weight", 1)),
                "spinPerGuest", 1,
                "showHistory", true
        )));
        when(activityInstanceRepository.findAllByEventIdAndEnabledFalseOrderBySortOrderAsc(10L)).thenReturn(List.of(disabled));

        List<kg.birthday.invite.dto.ActivityView> views = activityService.getDisabledActivityViews(10L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).instance()).isSameAs(disabled);
    }

    @Test
    void legacyPhotoChallengeConfigRendersAsMemeChallenge() {
        ActivityInstance legacy = instance("photo-challenge");
        legacy.setDisplayName("Фото-челлендж");
        legacy.setConfig(new java.util.LinkedHashMap<>(Map.of(
                "title", "Фото-челлендж",
                "challenges", List.of("Сделай фото"),
                "allowVoting", true,
                "showGallery", true
        )));
        when(activityInstanceRepository.findAllByEventIdAndEnabledTrueOrderBySortOrderAsc(10L)).thenReturn(List.of(legacy));

        List<kg.birthday.invite.dto.ActivityView> views = activityService.getEnabledActivityViews(10L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).instance().getDisplayName()).isEqualTo("Мем-челлендж");
        assertThat(views.get(0).instance().getConfig()).containsEntry("title", "Мем-челлендж");
        assertThat((List<?>) views.get(0).instance().getConfig().get("memes")).hasSize(50);
        assertThat((List<?>) views.get(0).instance().getConfig().get("memes"))
                .allSatisfy(item -> assertThat((String) ((Map<?, ?>) item).get("imageUrl")).startsWith("https://i.imgflip.com/"));
    }

    @Test
    void legacyPhotoChallengeResultRendersWithDefaultMemeImage() {
        ActivityInstance legacy = instance("photo-challenge");
        legacy.setDisplayName("Фото-челлендж");
        legacy.setConfig(new java.util.LinkedHashMap<>(Map.of(
                "title", "Фото-челлендж",
                "challenges", List.of("Сделай фото"),
                "allowVoting", true,
                "showGallery", true
        )));
        ActivityResultEntity result = new ActivityResultEntity();
        Guest guest = new Guest();
        guest.setId(1L);
        result.setGuest(guest);
        result.setResultData(new java.util.LinkedHashMap<>(Map.of(
                "type", "meme-challenge",
                "legacyType", "photo-challenge",
                "action", "assign",
                "memeId", "legacy-1",
                "memeName", "Старый фото-челлендж",
                "memeImageUrl", ""
        )));
        when(activityInstanceRepository.findAllByEventIdAndEnabledTrueOrderBySortOrderAsc(10L)).thenReturn(List.of(legacy));
        when(activityResultRepository.findAllByActivityInstanceIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(result));

        List<kg.birthday.invite.dto.ActivityView> views = activityService.getEnabledActivityViews(10L);
        Map<String, Object> data = views.get(0).results().get(0).getResultData();

        assertThat(data.get("memeId")).isEqualTo("drake-hotline-bling");
        assertThat((String) data.get("memeImageUrl")).startsWith("https://i.imgflip.com/");
    }

    @Test
    void enablingAlreadyEnabledActivityReturnsExistingInstance() {
        ActivityInstance existing = instance("photo-challenge");
        when(activityInstanceRepository.findFirstByEventIdAndModuleSlugAndEnabledTrue(10L, "photo-challenge"))
                .thenReturn(Optional.of(existing));

        ActivityInstance result = activityService.enableActivity(10L, "photo-challenge", "Мем-челлендж");

        assertThat(result).isSameAs(existing);
    }

    private static ActivityInstance instance(String moduleSlug) {
        Event event = new Event();
        event.setId(10L);
        ActivityInstance instance = new ActivityInstance();
        instance.setId(1L);
        instance.setEvent(event);
        instance.setModuleSlug(moduleSlug);
        instance.setDisplayName("Old");
        instance.setEnabled(true);
        return instance;
    }

    private static Event event() {
        Event event = new Event();
        event.setId(10L);
        event.setName("Birthday");
        event.setDate(LocalDate.of(2026, 6, 30));
        return event;
    }

    private static Guest guest(Event event, Long id, String name) {
        Guest guest = new Guest();
        guest.setId(id);
        guest.setEvent(event);
        guest.setLabel(name);
        guest.setName(name);
        return guest;
    }

    private static ActivityInstance certificateInstance(Event event) {
        ActivityInstance instance = new ActivityInstance();
        instance.setId(7L);
        instance.setEvent(event);
        instance.setModuleSlug("guest-certificates");
        instance.setDisplayName("Certificates");
        instance.setEnabled(true);
        instance.setConfig(new GuestCertificatesModule().getDefaultConfig());
        return instance;
    }

    private static Map<String, Object> certificateConfig(String slug, String source, Map<String, Object> rule) {
        Map<String, Object> config = new LinkedHashMap<>(new GuestCertificatesModule().getDefaultConfig());
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("slug", slug);
        type.put("title", "Award");
        type.put("description", "Description");
        type.put("source", source);
        type.put("rule", rule);
        config.put("certificateTypes", List.of(type));
        return config;
    }

    private static ActivityResultEntity result(ActivityInstance instance, Guest guest, int points, Map<String, Object> data) {
        ActivityResultEntity result = new ActivityResultEntity();
        result.setActivityInstance(instance);
        result.setGuest(guest);
        result.setPoints(points);
        result.setResultData(new java.util.LinkedHashMap<>(data));
        return result;
    }
}
