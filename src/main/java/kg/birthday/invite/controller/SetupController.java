package kg.birthday.invite.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import kg.birthday.invite.dto.EventSetupRequest;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.service.AdminSessionService;
import kg.birthday.invite.service.AdminUserService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class SetupController {

    private final EventService eventService;
    private final ThemeService themeService;
    private final AdminUserService adminUserService;
    private final AdminSessionService adminSessionService;

    @GetMapping("/setup")
    public String setup(Model model) {
        if (adminUserService.hasUsers()) {
            return "redirect:/admin/login";
        }
        model.addAttribute("request", new EventSetupRequest());
        model.addAttribute("theme", themeService.defaultTheme());
        return "setup";
    }

    @PostMapping("/setup")
    public String create(
            @Valid @ModelAttribute("request") EventSetupRequest request,
            BindingResult bindingResult,
            HttpSession session,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("theme", themeService.defaultTheme());
            return "setup";
        }
        if (adminUserService.hasUsers()) {
            return "redirect:/admin/login";
        }
        Event event;
        try {
            event = adminUserService.createInitialSuperAdmin(request);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("theme", themeService.defaultTheme());
            model.addAttribute("setupError", exception.getMessage());
            return "setup";
        }
        AdminUser user = adminUserService.authenticate(request.getAdminLogin(), request.getPassword())
                .orElseThrow(() -> new IllegalStateException("Initial administrator was not created"));
        adminSessionService.signIn(session, user);
        adminSessionService.selectEvent(session, event.getId());
        return "redirect:/admin";
    }
}
