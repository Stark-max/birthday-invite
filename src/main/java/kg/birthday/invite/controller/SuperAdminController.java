package kg.birthday.invite.controller;

import jakarta.servlet.http.HttpSession;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.service.AdminSessionService;
import kg.birthday.invite.service.AdminUserService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class SuperAdminController {

    private final AdminSessionService adminSessionService;
    private final AdminUserService adminUserService;
    private final EventService eventService;
    private final ThemeService themeService;

    @GetMapping("/super-admin")
    public String dashboard(HttpSession session, Model model) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        populate(model);
        return "super-admin";
    }

    @GetMapping("/super-admin/admins")
    public String admins(HttpSession session, Model model) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        populate(model);
        return "super-admin-admins";
    }

    @PostMapping("/super-admin/admins")
    public String createAdmin(
            @RequestParam String login,
            @RequestParam String displayName,
            @RequestParam String temporaryPassword,
            HttpSession session,
            Model model
    ) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        try {
            adminUserService.createAdmin(login, displayName, temporaryPassword);
            return "redirect:/super-admin/admins?created";
        } catch (IllegalArgumentException exception) {
            populate(model);
            model.addAttribute("error", exception.getMessage());
            return "super-admin-admins";
        }
    }

    @PostMapping("/super-admin/admins/{userId}/enabled")
    public String setEnabled(
            @PathVariable Long userId,
            @RequestParam boolean enabled,
            HttpSession session
    ) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        adminUserService.setEnabled(userId, enabled);
        return "redirect:/super-admin/admins";
    }

    @PostMapping("/super-admin/admins/{userId}/reset-password")
    public String resetPassword(
            @PathVariable Long userId,
            @RequestParam String temporaryPassword,
            HttpSession session,
            Model model
    ) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        try {
            adminUserService.resetPassword(userId, temporaryPassword);
            return "redirect:/super-admin/admins?reset";
        } catch (IllegalArgumentException exception) {
            populate(model);
            model.addAttribute("error", exception.getMessage());
            return "super-admin-admins";
        }
    }

    @PostMapping("/super-admin/admins/{userId}/password")
    public String setPassword(
            @PathVariable Long userId,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            @RequestParam(defaultValue = "false") boolean mustChangePassword,
            HttpSession session,
            Model model
    ) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        if (!newPassword.equals(confirmPassword)) {
            populate(model);
            model.addAttribute("error", "Пароли не совпадают.");
            return "super-admin-admins";
        }
        try {
            adminUserService.setPasswordBySuperAdmin(userId, newPassword, mustChangePassword);
            return "redirect:/super-admin/admins?passwordChanged";
        } catch (IllegalArgumentException exception) {
            populate(model);
            model.addAttribute("error", exception.getMessage());
            return "super-admin-admins";
        }
    }

    @GetMapping("/super-admin/events")
    public String events(HttpSession session, Model model) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        populate(model);
        return "super-admin-events";
    }

    @PostMapping("/super-admin/events/{eventId}/open")
    public String openEvent(@PathVariable Long eventId, HttpSession session) {
        String redirect = accessRedirect(session);
        if (redirect != null) {
            return redirect;
        }
        Event event = eventService.getEvent(eventId);
        adminSessionService.selectEvent(session, event.getId());
        return "redirect:/admin";
    }

    private String accessRedirect(HttpSession session) {
        Optional<AdminUser> user = adminSessionService.currentUser(session);
        if (user.isEmpty()) {
            return "redirect:/admin/login";
        }
        if (user.get().isMustChangePassword()) {
            return "redirect:/admin/password/change";
        }
        if (!adminSessionService.isSuperAdmin(session)) {
            return "redirect:/admin";
        }
        return null;
    }

    private void populate(Model model) {
        model.addAttribute("admins", adminUserService.getAllUsers());
        model.addAttribute("events", eventService.getAllEvents());
        model.addAttribute("theme", themeService.defaultTheme());
    }
}
