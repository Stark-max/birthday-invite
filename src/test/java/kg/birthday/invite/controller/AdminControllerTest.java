package kg.birthday.invite.controller;

import kg.birthday.invite.config.SecurityConfig;
import kg.birthday.invite.dto.EventStatsResponse;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.entity.WishlistItem;
import kg.birthday.invite.enums.RsvpStatus;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.service.ActivityService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.GuestService;
import kg.birthday.invite.service.ThemeService;
import kg.birthday.invite.service.AdminSessionService;
import kg.birthday.invite.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

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

    @MockBean
    AdminSessionService adminSessionService;

    @MockBean
    AdminUserService adminUserService;

    @Test
    void adminPageRendersGuestLinksWhenAuthenticated() throws Exception {
        Event event = new Event();
        event.setId(1L);
        event.setName("Имя");
        event.setDate(LocalDate.of(2026, 6, 30));
        event.setShowGuestList(true);

        Guest guest = new Guest();
        guest.setId(1L);
        guest.setEvent(event);
        guest.setCode("ddzQhhJ3");
        guest.setLabel("Мен");
        guest.setStatus(RsvpStatus.PENDING);

        authenticate(event);
        when(guestService.getAllGuests(1L)).thenReturn(List.of(guest));
        when(guestService.getStats(1L)).thenReturn(new EventStatsResponse(1, 0, 0, 1));
        when(eventService.getWishlist(1L)).thenReturn(List.of());
        when(themeService.getGlobalTheme(1L)).thenReturn(Map.ofEntries(
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
                Map.entry("backgroundGradient", "none")
        ));
        when(activityService.getEnabledActivityViews(1L)).thenReturn(List.of());
        when(activityService.getEventLeaderboard(1L)).thenReturn(List.of());

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-copy-code=\"ddzQhhJ3\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("copyInviteLink(this.dataset.copyCode)")));
    }

    @Test
    void wishlistPageRendersDetailedGiftEditorWhenAuthenticated() throws Exception {
        Event event = new Event();
        event.setId(1L);
        event.setName("Birthday");
        event.setDate(LocalDate.of(2026, 6, 30));

        WishlistItem item = new WishlistItem();
        item.setId(3L);
        item.setEvent(event);
        item.setTitle("Instant camera");
        item.setPriceLabel("12 000 som");
        item.setImageUrl("https://example.com/camera.jpg");
        item.setProductUrl("https://example.com/camera");
        item.setComment("White color");

        authenticate(event);
        when(eventService.getWishlist(1L)).thenReturn(List.of(item));
        when(themeService.getGlobalTheme(1L)).thenReturn(Map.ofEntries(
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
        ));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/admin/wishlist").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"imageUrls\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Instant camera")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("12 000 som")));
    }

    @Test
    void eventProfilePageRendersCurrentEventFieldsWhenAuthenticated() throws Exception {
        Event event = new Event();
        event.setId(1L);
        event.setName("Birthday host");
        event.setDate(LocalDate.of(2026, 7, 1));
        event.setTime("19:30");
        event.setLocation("Garden");
        event.setLocationUrl("https://maps.example/place");
        event.setMessage("Welcome");
        event.setContactInfo("@host");
        event.setShowGuestList(false);

        authenticate(event);
        when(themeService.getGlobalTheme(1L)).thenReturn(Map.ofEntries(
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
        ));

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get("/admin/event").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"name\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Birthday host")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://maps.example/place")));
    }

    @Test
    void temporaryPasswordLoginRedirectsToRequiredPasswordChange() throws Exception {
        AdminUser user = new AdminUser();
        user.setId(15L);
        user.setRole(AdminRole.ADMIN);
        user.setEnabled(true);
        user.setMustChangePassword(true);
        when(adminUserService.authenticate("newadmin", "secret1")).thenReturn(java.util.Optional.of(user));

        mockMvc.perform(post("/admin/login")
                        .param("login", "newadmin")
                        .param("password", "secret1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/password/change"));

        verify(adminSessionService).signIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(user));
    }

    private void authenticate(Event event) {
        AdminUser user = new AdminUser();
        user.setId(10L);
        user.setRole(AdminRole.ADMIN);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        event.setOwnerAdmin(user);
        when(adminSessionService.currentUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(user));
        when(adminSessionService.currentEvent(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(event));
    }
}
