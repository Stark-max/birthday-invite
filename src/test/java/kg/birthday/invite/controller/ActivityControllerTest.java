package kg.birthday.invite.controller;

import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.config.SecurityConfig;
import kg.birthday.invite.dto.ActivityModuleInfo;
import kg.birthday.invite.dto.ActivityView;
import kg.birthday.invite.entity.ActivityInstance;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.service.ActivityService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.ThemeService;
import kg.birthday.invite.service.AdminSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@Import(SecurityConfig.class)
class ActivityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ActivityService activityService;

    @MockBean
    EventService eventService;

    @MockBean
    ThemeService themeService;

    @MockBean
    AdminSessionService adminSessionService;

    @Test
    void playEndpointReturnsModuleResult() throws Exception {
        when(activityService.playActivity(eq(7L), eq(11L), anyMap()))
                .thenReturn(ActivityResult.success("ok", 10, Map.of("saved", true)));

        mockMvc.perform(post("/activities/7/play")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestId\":11,\"answers\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.points").value(10));
    }

    @Test
    void adminActivitiesPageSplitsEnabledAndDisabledActivities() throws Exception {
        Event event = new Event();
        event.setId(1L);
        event.setName("Birthday");
        event.setDate(LocalDate.of(2026, 7, 1));
        ActivityInstance wheel = new ActivityInstance();
        wheel.setId(5L);
        wheel.setEvent(event);
        wheel.setModuleSlug("wheel");
        wheel.setDisplayName("Колесо");
        wheel.setConfig(new LinkedHashMap<>(Map.of(
                "title", "Крути",
                "mode", "prizes",
                "spinPerGuest", 1,
                "showHistory", true,
                "segments", List.of(Map.of("text", "Тост", "color", "#C9A84C", "weight", 1))
        )));
        wheel.setEnabled(true);

        ActivityInstance archivedWheel = new ActivityInstance();
        archivedWheel.setId(1L);
        archivedWheel.setEvent(event);
        archivedWheel.setModuleSlug("wheel");
        archivedWheel.setDisplayName("Старое колесо");
        archivedWheel.setConfig(new LinkedHashMap<>(Map.of(
                "title", "Старое",
                "mode", "prizes",
                "spinPerGuest", 1,
                "showHistory", true,
                "segments", List.of(Map.of("text", "Архив", "color", "#C9A84C", "weight", 1))
        )));
        archivedWheel.setEnabled(false);
        archivedWheel.setSortOrder(1);

        AdminUser user = new AdminUser();
        user.setId(10L);
        user.setRole(AdminRole.ADMIN);
        user.setEnabled(true);
        event.setOwnerAdmin(user);
        when(adminSessionService.currentEvent(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(event));
        when(activityService.getAvailableModules()).thenReturn(List.of());
        when(activityService.getEnabledActivityViews(1L)).thenReturn(List.of(view(wheel)));
        when(activityService.getDisabledActivityViews(1L)).thenReturn(List.of(view(archivedWheel)));
        when(activityService.getEventLeaderboard(1L)).thenReturn(List.of());
        when(themeService.getGlobalTheme(1L)).thenReturn(theme());

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/admin/activities").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"segmentTexts\"")))
                .andExpect(content().string(containsString("wheel-segment-template-5")))
                .andExpect(content().string(containsString("Отключённые / архив")))
                .andExpect(content().string(containsString("Старое колесо")))
                .andExpect(content().string(containsString("Отключена")))
                .andExpect(content().string(not(containsString("wheel-segment-template-1"))))
                .andExpect(content().string(not(containsString("name=\"configJson\""))));
    }

    private static ActivityView view(ActivityInstance instance) {
        ActivityModuleInfo module = new ActivityModuleInfo(
                instance.getModuleSlug(),
                instance.getDisplayName(),
                "Описание",
                "A",
                "fragments/activity/" + instance.getModuleSlug() + "-guest",
                "fragments/activity/" + instance.getModuleSlug() + "-admin"
        );
        return new ActivityView(instance, module, "{}", module.guestFragmentName(), module.adminFragmentName(), List.of(), 0, "—");
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
                Map.entry("cardShadow", "none"),
                Map.entry("backgroundGradient", "none")
        );
    }
}
