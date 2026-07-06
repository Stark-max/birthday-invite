package kg.birthday.invite.controller;

import jakarta.servlet.http.HttpSession;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.AdminSessionService;
import kg.birthday.invite.service.GuestService;
import kg.birthday.invite.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ThemeController {

    private static final String[] THEME_KEYS = {
            "colorBg", "colorBgCard", "colorText", "colorTextSecondary", "colorAccent",
            "colorAccentLight", "colorAccentDark", "colorSuccess", "colorDanger",
            "fontDisplay", "fontBody", "borderRadius", "cardShadow", "backgroundPattern",
            "backgroundGradient", "headerStyle", "animationStyle", "visualEffect"
    };

    private final EventService eventService;
    private final GuestService guestService;
    private final ThemeService themeService;
    private final AdminSessionService adminSessionService;

    @GetMapping("/admin/themes")
    public String themes(HttpSession session, Model model) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        model.addAttribute("event", event);
        model.addAttribute("presets", themeService.getAllPresets(event.getId()));
        model.addAttribute("guests", guestService.getAllGuests(event.getId()));
        model.addAttribute("currentTheme", themeService.getGlobalTheme(event.getId()));
        model.addAttribute("theme", themeService.getGlobalTheme(event.getId()));
        return "admin-themes";
    }

    @PostMapping("/admin/themes/global")
    public String setGlobal(@RequestParam Map<String, String> params, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        String presetSlug = params.get("presetSlug");
        if (presetSlug != null && !presetSlug.isBlank()) {
            themeService.applyPresetToEvent(event.getId(), presetSlug);
        } else {
            themeService.setGlobalTheme(event.getId(), extractTheme(params));
        }
        return "redirect:/admin/themes";
    }

    @PostMapping("/admin/themes/effect")
    public String setVisualEffect(@RequestParam String visualEffect, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        themeService.setVisualEffect(event.getId(), visualEffect);
        return "redirect:/admin/themes?effectSaved";
    }

    @PostMapping("/admin/themes/guest/{guestId}")
    public String setGuestTheme(@PathVariable Long guestId, @RequestParam Map<String, String> params, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        String presetSlug = params.get("presetSlug");
        if (presetSlug != null && !presetSlug.isBlank()) {
            themeService.applyPresetToGuest(event.getId(), guestId, presetSlug);
        } else {
            themeService.setPersonalTheme(event.getId(), guestId, extractTheme(params));
        }
        return "redirect:/admin/themes";
    }

    @PostMapping("/admin/themes/guest/{guestId}/reset")
    public String resetGuestTheme(@PathVariable Long guestId, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        themeService.resetPersonalTheme(event.getId(), guestId);
        return "redirect:/admin/themes";
    }

    @PostMapping("/admin/themes/presets")
    public String createPreset(@RequestParam String name, @RequestParam Map<String, String> params, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        themeService.createCustomPreset(event.getId(), name, extractTheme(params));
        return "redirect:/admin/themes";
    }

    @DeleteMapping("/admin/themes/presets/{id}")
    public String deletePreset(@PathVariable Long id, HttpSession session) {
        return deletePresetPost(id, session);
    }

    @PostMapping("/admin/themes/presets/{id}/delete")
    public String deletePresetPost(@PathVariable Long id, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        themeService.deleteCustomPreset(event.getId(), id);
        return "redirect:/admin/themes";
    }

    private String accessRedirect(HttpSession session) {
        return adminSessionService.currentUser(session)
                .map(user -> user.isMustChangePassword()
                        ? "redirect:/admin/password/change"
                        : "redirect:/admin/event/new")
                .orElse("redirect:/admin/login");
    }

    private Map<String, String> extractTheme(Map<String, String> params) {
        Map<String, String> theme = new LinkedHashMap<>();
        for (String key : THEME_KEYS) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                theme.put(key, value);
            }
        }
        return theme;
    }
}
