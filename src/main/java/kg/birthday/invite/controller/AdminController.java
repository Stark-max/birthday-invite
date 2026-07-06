package kg.birthday.invite.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kg.birthday.invite.dto.EventProfileRequest;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.enums.AdminRole;
import kg.birthday.invite.service.ActivityService;
import kg.birthday.invite.service.AdminSessionService;
import kg.birthday.invite.service.AdminUserService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.GuestService;
import kg.birthday.invite.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final EventService eventService;
    private final GuestService guestService;
    private final ThemeService themeService;
    private final ActivityService activityService;
    private final AdminUserService adminUserService;
    private final AdminSessionService adminSessionService;

    @GetMapping("/admin/login")
    public String login(Model model, HttpSession session,
                        @RequestParam(value = "error", required = false) String error) {
        if (!adminUserService.hasUsers()) {
            return "redirect:/setup";
        }
        Optional<AdminUser> currentUser = adminSessionService.currentUser(session);
        if (currentUser.isPresent()) {
            return homeFor(currentUser.get(), session);
        }
        model.addAttribute("error", error != null);
        model.addAttribute("theme", themeService.defaultTheme());
        return "admin-login";
    }

    @PostMapping("/admin/login")
    public String doLogin(
            @RequestParam String login,
            @RequestParam String password,
            HttpSession session,
            Model model
    ) {
        Optional<AdminUser> userOptional = adminUserService.authenticate(login, password);
        if (userOptional.isEmpty()) {
            model.addAttribute("error", true);
            model.addAttribute("theme", themeService.defaultTheme());
            return "admin-login";
        }
        AdminUser user = userOptional.get();
        adminSessionService.signIn(session, user);
        if (user.isMustChangePassword()) {
            return "redirect:/admin/password/change";
        }
        return homeFor(user, session);
    }

    @GetMapping("/admin/password/change")
    public String changePassword(HttpSession session, Model model) {
        Optional<AdminUser> user = adminSessionService.currentUser(session);
        if (user.isEmpty()) {
            return "redirect:/admin/login";
        }
        model.addAttribute("adminUser", user.get());
        model.addAttribute("theme", themeService.defaultTheme());
        return "admin-password";
    }

    @PostMapping("/admin/password/change")
    public String changePassword(
            @RequestParam String currentPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            HttpSession session,
            Model model
    ) {
        Optional<AdminUser> userOptional = adminSessionService.currentUser(session);
        if (userOptional.isEmpty()) {
            return "redirect:/admin/login";
        }
        AdminUser user = userOptional.get();
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("adminUser", user);
            model.addAttribute("theme", themeService.defaultTheme());
            model.addAttribute("error", "Новые пароли не совпадают.");
            return "admin-password";
        }
        try {
            adminUserService.changePassword(user.getId(), currentPassword, newPassword);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("adminUser", user);
            model.addAttribute("theme", themeService.defaultTheme());
            model.addAttribute("error", exception.getMessage());
            return "admin-password";
        }
        return user.getRole() == AdminRole.SUPER_ADMIN
                ? "redirect:/super-admin"
                : adminSessionService.currentEvent(session).isPresent()
                ? "redirect:/admin"
                : "redirect:/admin/event/new";
    }

    @GetMapping("/admin")
    public String admin(HttpSession session, Model model) {
        Optional<Event> eventOptional = accessibleEvent(session);
        if (eventOptional.isEmpty()) {
            return missingEventRedirect(session);
        }
        Event event = eventOptional.get();
        model.addAttribute("event", event);
        model.addAttribute("guests", guestService.getAllGuests(event.getId()));
        model.addAttribute("stats", guestService.getStats(event.getId()));
        model.addAttribute("wishlist", eventService.getWishlist(event.getId()));
        model.addAttribute("theme", themeService.getGlobalTheme(event.getId()));
        model.addAttribute("activityViews", activityService.getEnabledActivityViews(event.getId()));
        model.addAttribute("leaderboard", activityService.getEventLeaderboard(event.getId()));
        return "admin";
    }

    @GetMapping("/admin/wishlist")
    public String wishlist(HttpSession session, Model model) {
        Optional<Event> eventOptional = accessibleEvent(session);
        if (eventOptional.isEmpty()) {
            return missingEventRedirect(session);
        }
        Event event = eventOptional.get();
        model.addAttribute("event", event);
        model.addAttribute("wishlist", eventService.getWishlist(event.getId()));
        model.addAttribute("theme", themeService.getGlobalTheme(event.getId()));
        return "admin-wishlist";
    }

    @GetMapping("/admin/event")
    public String eventProfile(HttpSession session, Model model) {
        Optional<Event> eventOptional = accessibleEvent(session);
        if (eventOptional.isEmpty()) {
            return missingEventRedirect(session);
        }
        Event event = eventOptional.get();
        model.addAttribute("event", event);
        model.addAttribute("request", toProfileRequest(event));
        model.addAttribute("theme", themeService.getGlobalTheme(event.getId()));
        return "admin-event";
    }

    @PostMapping("/admin/event")
    public String updateEventProfile(
            @Valid @ModelAttribute("request") EventProfileRequest request,
            BindingResult bindingResult,
            HttpSession session,
            Model model
    ) {
        Optional<Event> eventOptional = accessibleEvent(session);
        if (eventOptional.isEmpty()) {
            return missingEventRedirect(session);
        }
        Event event = eventOptional.get();
        if (bindingResult.hasErrors()) {
            model.addAttribute("event", event);
            model.addAttribute("theme", themeService.getGlobalTheme(event.getId()));
            return "admin-event";
        }
        eventService.updateEventProfile(event.getId(), request);
        return "redirect:/admin/event?saved";
    }

    @GetMapping("/admin/event/new")
    public String newEvent(HttpSession session, Model model) {
        Optional<AdminUser> userOptional = activeUser(session);
        if (userOptional.isEmpty()) {
            return "redirect:/admin/login";
        }
        AdminUser user = userOptional.get();
        if (user.getRole() == AdminRole.SUPER_ADMIN) {
            return "redirect:/super-admin/events";
        }
        Optional<Event> existing = eventService.findByOwnerAdminId(user.getId());
        if (existing.isPresent()) {
            adminSessionService.selectEvent(session, existing.get().getId());
            return "redirect:/admin";
        }
        EventProfileRequest request = new EventProfileRequest();
        request.setShowGuestList(true);
        model.addAttribute("request", request);
        model.addAttribute("theme", themeService.defaultTheme());
        return "admin-event-new";
    }

    @PostMapping("/admin/event/new")
    public String createEvent(
            @Valid @ModelAttribute("request") EventProfileRequest request,
            BindingResult bindingResult,
            HttpSession session,
            Model model
    ) {
        Optional<AdminUser> userOptional = activeUser(session);
        if (userOptional.isEmpty()) {
            return "redirect:/admin/login";
        }
        AdminUser user = userOptional.get();
        if (user.getRole() != AdminRole.ADMIN) {
            return "redirect:/super-admin/events";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("theme", themeService.defaultTheme());
            return "admin-event-new";
        }
        Event event = eventService.createEvent(user, request);
        adminSessionService.selectEvent(session, event.getId());
        return "redirect:/admin";
    }

    @PostMapping("/admin/guests")
    public String addGuest(@RequestParam String label, HttpSession session) {
        Optional<Event> event = accessibleEvent(session);
        if (event.isEmpty()) {
            return missingEventRedirect(session);
        }
        guestService.addGuest(event.get().getId(), label);
        return "redirect:/admin";
    }

    @PostMapping("/admin/guests/{id}/delete")
    public String deleteGuest(@PathVariable Long id, HttpSession session) {
        Optional<Event> event = accessibleEvent(session);
        if (event.isEmpty()) {
            return missingEventRedirect(session);
        }
        guestService.removeGuest(event.get().getId(), id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/wishlist")
    public String updateWishlist(
            @RequestParam(value = "itemIds", required = false) List<String> itemIds,
            @RequestParam(value = "titles", required = false) List<String> titles,
            @RequestParam(value = "imageUrls", required = false) List<String> imageUrls,
            @RequestParam(value = "priceLabels", required = false) List<String> priceLabels,
            @RequestParam(value = "comments", required = false) List<String> comments,
            @RequestParam(value = "productUrls", required = false) List<String> productUrls,
            HttpSession session
    ) {
        Optional<Event> event = accessibleEvent(session);
        if (event.isEmpty()) {
            return missingEventRedirect(session);
        }
        eventService.updateWishlistDetails(
                event.get().getId(),
                itemIds == null ? List.of() : itemIds,
                titles == null ? List.of() : titles,
                imageUrls == null ? List.of() : imageUrls,
                priceLabels == null ? List.of() : priceLabels,
                comments == null ? List.of() : comments,
                productUrls == null ? List.of() : productUrls
        );
        return "redirect:/admin/wishlist?saved";
    }

    @PostMapping("/admin/settings/toggle-guestlist")
    public String toggleGuestList(HttpSession session) {
        Optional<Event> event = accessibleEvent(session);
        if (event.isEmpty()) {
            return missingEventRedirect(session);
        }
        eventService.toggleGuestList(event.get().getId());
        return "redirect:/admin";
    }

    @GetMapping("/admin/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login";
    }

    private Optional<AdminUser> activeUser(HttpSession session) {
        return adminSessionService.currentUser(session).filter(user -> !user.isMustChangePassword());
    }

    private Optional<Event> accessibleEvent(HttpSession session) {
        if (activeUser(session).isEmpty()) {
            return Optional.empty();
        }
        return adminSessionService.currentEvent(session);
    }

    private String missingEventRedirect(HttpSession session) {
        Optional<AdminUser> user = adminSessionService.currentUser(session);
        if (user.isEmpty()) {
            return "redirect:/admin/login";
        }
        if (user.get().isMustChangePassword()) {
            return "redirect:/admin/password/change";
        }
        return user.get().getRole() == AdminRole.SUPER_ADMIN
                ? "redirect:/super-admin/events"
                : "redirect:/admin/event/new";
    }

    private String homeFor(AdminUser user, HttpSession session) {
        if (user.getRole() == AdminRole.SUPER_ADMIN) {
            return "redirect:/super-admin";
        }
        Optional<Event> event = eventService.findByOwnerAdminId(user.getId());
        event.ifPresent(value -> adminSessionService.selectEvent(session, value.getId()));
        return event.isPresent() ? "redirect:/admin" : "redirect:/admin/event/new";
    }

    private static EventProfileRequest toProfileRequest(Event event) {
        EventProfileRequest request = new EventProfileRequest();
        request.setName(event.getName());
        request.setDate(event.getDate());
        request.setTime(event.getTime());
        request.setLocation(event.getLocation());
        request.setLocationUrl(event.getLocationUrl());
        request.setMessage(event.getMessage());
        request.setContactInfo(event.getContactInfo());
        request.setShowGuestList(event.isShowGuestList());
        return request;
    }
}
