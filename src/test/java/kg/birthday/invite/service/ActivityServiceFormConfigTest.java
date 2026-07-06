package kg.birthday.invite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActivityServiceFormConfigTest {

    ActivityInstanceRepository activityInstanceRepository;
    ActivityResultRepository activityResultRepository;
    ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityInstanceRepository = mock(ActivityInstanceRepository.class);
        activityResultRepository = mock(ActivityResultRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        ActivityRegistry registry = new ActivityRegistry(List.of(
                new WheelOfFortuneModule(),
                new QuizModule(),
                new PhotoChallengeModule(),
                new TruthOrDareModule()
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
}
