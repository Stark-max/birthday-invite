package kg.birthday.invite.controller;

import kg.birthday.invite.config.SecurityConfig;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.service.AdminSessionService;
import kg.birthday.invite.service.AdminUserService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.ThemeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuperAdminController.class)
@Import(SecurityConfig.class)
class SuperAdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AdminSessionService adminSessionService;

    @MockBean
    AdminUserService adminUserService;

    @MockBean
    EventService eventService;

    @MockBean
    ThemeService themeService;

    @Test
    void ordinaryAdminCannotOpenSuperAdminSection() throws Exception {
        AdminUser admin = user(AdminRole.ADMIN);
        when(adminSessionService.currentUser(any())).thenReturn(Optional.of(admin));
        when(adminSessionService.isSuperAdmin(any())).thenReturn(false);

        mockMvc.perform(get("/super-admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void superAdminCanOpenDashboard() throws Exception {
        AdminUser superAdmin = user(AdminRole.SUPER_ADMIN);
        when(adminSessionService.currentUser(any())).thenReturn(Optional.of(superAdmin));
        when(adminSessionService.isSuperAdmin(any())).thenReturn(true);
        when(adminUserService.getAllUsers()).thenReturn(List.of(superAdmin));
        when(eventService.getAllEvents()).thenReturn(List.of());
        when(themeService.defaultTheme()).thenReturn(Map.of());

        mockMvc.perform(get("/super-admin"))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminPagesRenderAdminAndEventManagement() throws Exception {
        AdminUser superAdmin = user(AdminRole.SUPER_ADMIN);
        AdminUser admin = user(AdminRole.ADMIN);
        admin.setId(2L);
        admin.setLogin("owner");
        admin.setDisplayName("Owner");
        Event event = new Event();
        event.setId(7L);
        event.setName("Birthday");
        event.setDate(java.time.LocalDate.of(2026, 9, 1));
        event.setPublicSlug("event-7");
        event.setOwnerAdmin(admin);
        when(adminSessionService.currentUser(any())).thenReturn(Optional.of(superAdmin));
        when(adminSessionService.isSuperAdmin(any())).thenReturn(true);
        when(adminUserService.getAllUsers()).thenReturn(List.of(superAdmin, admin));
        when(eventService.getAllEvents()).thenReturn(List.of(event));
        when(themeService.defaultTheme()).thenReturn(Map.of());

        mockMvc.perform(get("/super-admin/admins"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/super-admin/events"))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanSetAdminPassword() throws Exception {
        AdminUser superAdmin = user(AdminRole.SUPER_ADMIN);
        when(adminSessionService.currentUser(any())).thenReturn(Optional.of(superAdmin));
        when(adminSessionService.isSuperAdmin(any())).thenReturn(true);

        mockMvc.perform(post("/super-admin/admins/2/password")
                        .param("newPassword", "newpass1")
                        .param("confirmPassword", "newpass1")
                        .param("mustChangePassword", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/super-admin/admins?passwordChanged"));

        verify(adminUserService).setPasswordBySuperAdmin(eq(2L), eq("newpass1"), eq(true));
    }

    private static AdminUser user(AdminRole role) {
        AdminUser user = new AdminUser();
        user.setId(1L);
        user.setRole(role);
        user.setEnabled(true);
        user.setMustChangePassword(false);
        user.setLogin("admin");
        user.setDisplayName("Admin");
        return user;
    }
}
