package kg.birthday.invite.controller;

import kg.birthday.invite.config.SecurityConfig;
import kg.birthday.invite.dto.ActivityModuleInfo;
import kg.birthday.invite.dto.ActivityView;
import kg.birthday.invite.entity.ActivityInstance;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.enums.RsvpStatus;
import kg.birthday.invite.service.ActivityService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.GuestService;
import kg.birthday.invite.service.ThemeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@WebMvcTest(GuestController.class)
@Import(SecurityConfig.class)
class GuestControllerActivityRenderTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EventService eventService;

    @MockBean
    GuestService guestService;

    @MockBean
    ThemeService themeService;

    @MockBean
    ActivityService activityService;

    @Test
    void acceptedPageRendersOnlyMatchingActivityFragmentsAndRawJson() throws Exception {
        Event event = new Event();
        event.setId(1L);
        event.setName("Имя");
        event.setDate(LocalDate.of(2026, 6, 30));
        event.setShowGuestList(true);

        Guest guest = new Guest();
        guest.setId(1L);
        guest.setEvent(event);
        guest.setCode("abc12345");
        guest.setLabel("Гость");
        guest.setName("Гость");
        guest.setStatus(RsvpStatus.ACCEPTED);

        ActivityInstance photo = activity(10L, event, "photo-challenge", "Мем-челлендж",
                Map.of(
                        "title", "Мем-челлендж",
                        "instructions", "Придумай подпись",
                        "memes", List.of(Map.of(
                                "id", "drake",
                                "name", "Drake Hotline Bling",
                                "region", "US",
                                "prompt", "Выбор гостя",
                                "imageUrl", "",
                                "accent", "#ffd166",
                                "emoji", "🎧"
                        )),
                        "showGallery", true,
                        "allowVoting", true
                ));
        ActivityInstance quiz = activity(11L, event, "quiz", "Викторина",
                Map.of("questions", List.of(Map.of(
                        "id", "q1",
                        "text", "Вопрос",
                        "type", "text_input",
                        "correctAnswer", "Ответ",
                        "points", 5
                ))));

        when(guestService.getGuestByCode("abc12345")).thenReturn(Optional.of(guest));
        when(eventService.getWishlist(1L)).thenReturn(List.of());
        when(guestService.getAcceptedGuests(1L)).thenReturn(List.of(guest));
        when(activityService.getEnabledActivityViews(1L)).thenReturn(List.of(
                view(photo, "🖼", "{\"memes\":[{\"id\":\"drake\",\"name\":\"Drake Hotline Bling\"}]}"),
                view(quiz, "🧠", "{\"questions\":[{\"id\":\"q1\",\"text\":\"Вопрос\"}]}")
        ));
        when(activityService.getEventLeaderboard(1L)).thenReturn(List.of());
        when(themeService.getEffectiveTheme(guest)).thenReturn(theme());

        mockMvc.perform(get("/invite/abc12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("challenge-result-10")))
                .andExpect(content().string(not(containsString("quiz-root-10"))))
                .andExpect(content().string(containsString("quiz-root-11")))
                .andExpect(content().string(containsString("effect-spring")))
                .andExpect(content().string(containsString("scene-spring")))
                .andExpect(content().string(containsString("{\"questions\"")))
                .andExpect(content().string(not(containsString("&quot;questions&quot;"))));
    }

    @Test
    void rootRedirectsToLoginWhenSeveralEventsExist() throws Exception {
        when(eventService.countEvents()).thenReturn(2L);

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void publicSlugRendersOnlyItsEvent() throws Exception {
        Event event = new Event();
        event.setId(2L);
        event.setName("Отдельное событие");
        event.setDate(LocalDate.of(2026, 8, 1));
        event.setPublicSlug("event-private-link");
        when(eventService.findByPublicSlug("event-private-link")).thenReturn(Optional.of(event));
        when(themeService.getGlobalTheme(event)).thenReturn(theme());

        mockMvc.perform(get("/e/event-private-link"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Отдельное событие")));
    }

    private static ActivityInstance activity(Long id, Event event, String slug, String name, Map<String, Object> config) {
        ActivityInstance instance = new ActivityInstance();
        instance.setId(id);
        instance.setEvent(event);
        instance.setModuleSlug(slug);
        instance.setDisplayName(name);
        instance.setConfig(new LinkedHashMap<>(config));
        instance.setEnabled(true);
        return instance;
    }

    private static ActivityView view(ActivityInstance instance, String icon, String configJson) {
        ActivityModuleInfo module = new ActivityModuleInfo(
                instance.getModuleSlug(),
                instance.getDisplayName(),
                "Описание",
                icon,
                "fragments/activity/" + instance.getModuleSlug() + "-guest",
                "fragments/activity/" + instance.getModuleSlug() + "-admin"
        );
        return new ActivityView(instance, module, configJson, module.guestFragmentName(), module.adminFragmentName(), List.of(), 0, "—");
    }

    private static Map<String, String> theme() {
        return Map.ofEntries(
                Map.entry("colorBg", "#FBF7F0"),
                Map.entry("colorBgCard", "#FFFFFF"),
                Map.entry("colorText", "#2C2825"),
                Map.entry("colorTextSecondary", "#4A4541"),
                Map.entry("colorAccent", "#C9A84C"),
                Map.entry("colorAccentLight", "#E8D5A3"),
                Map.entry("colorAccentDark", "#A07D2E"),
                Map.entry("colorSuccess", "#7D9B76"),
                Map.entry("colorDanger", "#C07080"),
                Map.entry("fontDisplay", "Cormorant Garamond"),
                Map.entry("fontBody", "Outfit"),
                Map.entry("borderRadius", "16px"),
                Map.entry("cardShadow", "0 1px 3px rgba(0,0,0,0.04)"),
                Map.entry("backgroundGradient", "none"),
                Map.entry("visualEffect", "spring")
        );
    }
}
