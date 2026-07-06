package kg.birthday.invite.controller;

import jakarta.servlet.http.HttpSession;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.service.ActivityService;
import kg.birthday.invite.service.ThemeService;
import kg.birthday.invite.service.AdminSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final ThemeService themeService;
    private final AdminSessionService adminSessionService;

    @GetMapping("/admin/activities")
    public String activities(
            @RequestParam(required = false) String certificatesMessage,
            HttpSession session,
            Model model
    ) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        List<kg.birthday.invite.dto.ActivityView> enabledActivityViews = activityService.getEnabledActivityViews(event.getId());
        model.addAttribute("event", event);
        model.addAttribute("modules", activityService.getAvailableModules());
        model.addAttribute("enabledActivityViews", enabledActivityViews);
        model.addAttribute("enabledModuleSlugs", enabledActivityViews.stream()
                .map(view -> view.instance().getModuleSlug())
                .toList());
        model.addAttribute("disabledActivityViews", activityService.getDisabledActivityViews(event.getId()));
        model.addAttribute("acceptedGuests", activityService.getAcceptedGuests(event.getId()));
        model.addAttribute("leaderboard", activityService.getEventLeaderboard(event.getId()));
        model.addAttribute("hasPositiveActivityResults", activityService.hasPositiveActivityResults(event.getId()));
        model.addAttribute("certificatesMessage", certificatesMessage);
        model.addAttribute("theme", themeService.getGlobalTheme(event.getId()));
        return "admin-activities";
    }

    @PostMapping("/admin/activities/enable")
    public String enable(
            @RequestParam String slug,
            @RequestParam(required = false) String displayName,
            HttpSession session
    ) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        activityService.enableActivity(event.getId(), slug, displayName);
        return "redirect:/admin/activities";
    }

    @PostMapping("/admin/activities/{instanceId}/disable")
    public String disable(@PathVariable Long instanceId, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        activityService.disableActivity(event.getId(), instanceId);
        return "redirect:/admin/activities";
    }

    @PostMapping("/admin/activities/{instanceId}/config")
    public String updateConfig(
            @PathVariable Long instanceId,
            @RequestParam MultiValueMap<String, String> params,
            HttpSession session
    ) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        String configJson = params.getFirst("configJson");
        if (configJson != null && !configJson.isBlank()) {
            activityService.updateActivityConfigFromJson(event.getId(), instanceId, configJson);
        } else {
            activityService.updateActivityConfigFromForm(event.getId(), instanceId, params);
        }
        return "redirect:/admin/activities";
    }

    @GetMapping("/admin/activities/{instanceId}/results")
    @ResponseBody
    public List<Map<String, Object>> results(@PathVariable Long instanceId, HttpSession session) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return List.of();
        }
        return activityService.getResults(event.getId(), instanceId).stream().map(this::resultMap).toList();
    }

    @PostMapping("/admin/activities/{instanceId}/certificates/award")
    public String awardCertificate(
            @PathVariable Long instanceId,
            @RequestParam Long guestId,
            @RequestParam String certificateTypeSlug,
            @RequestParam(required = false) String customTitle,
            @RequestParam(required = false) String customText,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        activityService.awardCertificate(event.getId(), instanceId, guestId, certificateTypeSlug, customTitle, customText);
        redirectAttributes.addAttribute("certificatesMessage", "Сертификат выдан. Он появился у выбранного гостя на странице «Мои награды».");
        return "redirect:/admin/activities";
    }

    @PostMapping("/admin/activities/{instanceId}/certificates/generate")
    public String generateCertificates(
            @PathVariable Long instanceId,
            @RequestParam(defaultValue = "false") boolean overwriteExisting,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        List<ActivityResult> generated = activityService.generateCertificates(event.getId(), instanceId, overwriteExisting);
        if (generated.isEmpty()) {
            redirectAttributes.addAttribute("certificatesMessage", "Новые сертификаты не созданы: нет результатов с очками или такие награды уже есть.");
        } else {
            redirectAttributes.addAttribute("certificatesMessage", "Сгенерировано сертификатов: " + generated.size() + ".");
        }
        return "redirect:/admin/activities";
    }

    @PostMapping("/admin/activities/{instanceId}/certificates/{resultId}/delete")
    public String deleteCertificate(
            @PathVariable Long instanceId,
            @PathVariable Long resultId,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        Event event = adminSessionService.currentEvent(session).orElse(null);
        if (event == null) {
            return accessRedirect(session);
        }
        activityService.deleteCertificate(event.getId(), instanceId, resultId);
        redirectAttributes.addAttribute("certificatesMessage", "Сертификат удалён.");
        return "redirect:/admin/activities";
    }

    @PostMapping("/activities/{instanceId}/play")
    @ResponseBody
    public ResponseEntity<ActivityResult> play(
            @PathVariable Long instanceId,
            @RequestBody Map<String, Object> action
    ) {
        Long guestId = number(action.get("guestId"));
        if (guestId == null) {
            return ResponseEntity.badRequest().body(ActivityResult.error("guestId обязателен."));
        }
        return ResponseEntity.ok(activityService.playActivity(instanceId, guestId, action));
    }

    @GetMapping("/activities/{instanceId}/leaderboard")
    @ResponseBody
    public List<Map<String, Object>> leaderboard(@PathVariable Long instanceId) {
        return activityService.getResults(instanceId).stream()
                .filter(result -> result.getPoints() > 0)
                .collect(java.util.stream.Collectors.groupingBy(
                        result -> result.getGuest().getId(),
                        java.util.stream.Collectors.toList()
                ))
                .values().stream()
                .map(results -> {
                    ActivityResultEntity first = results.get(0);
                    int points = results.stream().mapToInt(ActivityResultEntity::getPoints).sum();
                    return Map.<String, Object>of(
                            "guestId", first.getGuest().getId(),
                            "guestName", first.getGuest().getName() == null ? first.getGuest().getLabel() : first.getGuest().getName(),
                            "points", points
                    );
                })
                .sorted((left, right) -> Integer.compare((Integer) right.get("points"), (Integer) left.get("points")))
                .toList();
    }

    private Map<String, Object> resultMap(ActivityResultEntity result) {
        return Map.of(
                "id", result.getId(),
                "guest", result.getGuest().getName() == null ? result.getGuest().getLabel() : result.getGuest().getName(),
                "points", result.getPoints(),
                "data", result.getResultData(),
                "createdAt", result.getCreatedAt()
        );
    }

    private Long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String accessRedirect(HttpSession session) {
        return adminSessionService.currentUser(session)
                .map(user -> user.isMustChangePassword()
                        ? "redirect:/admin/password/change"
                        : "redirect:/admin/event/new")
                .orElse("redirect:/admin/login");
    }
}
