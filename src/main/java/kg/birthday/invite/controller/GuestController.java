package kg.birthday.invite.controller;

import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.entity.ActivityResultEntity;
import kg.birthday.invite.enums.RsvpStatus;
import kg.birthday.invite.service.ActivityService;
import kg.birthday.invite.service.EventService;
import kg.birthday.invite.service.GuestService;
import kg.birthday.invite.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
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
public class GuestController {

    private final EventService eventService;
    private final GuestService guestService;
    private final ThemeService themeService;
    private final ActivityService activityService;

    @GetMapping("/")
    public String landing(Model model) {
        long eventCount = eventService.countEvents();
        if (eventCount == 0) {
            return "redirect:/setup";
        }
        if (eventCount > 1) {
            return "redirect:/admin/login";
        }
        Event event = eventService.getEvent();
        return renderLanding(event, model);
    }

    @GetMapping("/e/{publicSlug}")
    public String eventLanding(@PathVariable String publicSlug, Model model) {
        Event event = eventService.findByPublicSlug(publicSlug).orElse(null);
        if (event == null) {
            model.addAttribute("message", "Приглашение не найдено");
            model.addAttribute("theme", themeService.defaultTheme());
            return "error";
        }
        return renderLanding(event, model);
    }

    private String renderLanding(Event event, Model model) {
        model.addAttribute("event", event);
        model.addAttribute("theme", themeService.getGlobalTheme(event));
        return "landing";
    }

    @GetMapping("/invite/{code}")
    public String invite(@PathVariable String code, Model model) {
        Guest guest = guestService.getGuestByCode(code).orElse(null);
        if (guest == null) {
            model.addAttribute("message", "Приглашение не найдено");
            model.addAttribute("theme", themeService.defaultTheme());
            return "error";
        }
        Event event = guest.getEvent();
        model.addAttribute("event", event);
        model.addAttribute("guest", guest);
        model.addAttribute("theme", themeService.getEffectiveTheme(guest));

        if (guest.getStatus() == RsvpStatus.ACCEPTED) {
            model.addAttribute("wishlist", eventService.getWishlist(event.getId()));
            model.addAttribute("acceptedGuests", guestService.getAcceptedGuests(event.getId()));
            model.addAttribute("activityViews", activityService.getEnabledActivityViews(event.getId()));
            model.addAttribute("leaderboard", activityService.getEventLeaderboard(event.getId()));
            return "guest-accepted";
        }
        if (guest.getStatus() == RsvpStatus.DECLINED) {
            return "guest-declined";
        }
        return "guest";
    }

    @GetMapping("/invite/{code}/certificates")
    public String certificates(@PathVariable String code, Model model) {
        Guest guest = guestService.getGuestByCode(code).orElse(null);
        if (guest == null) {
            model.addAttribute("message", "Приглашение не найдено");
            model.addAttribute("theme", themeService.defaultTheme());
            return "error";
        }
        if (guest.getStatus() != RsvpStatus.ACCEPTED) {
            return "redirect:/invite/" + code;
        }
        Event event = guest.getEvent();
        List<ActivityResultEntity> certificates = activityService.getGuestCertificates(event.getId(), guest.getId());
        model.addAttribute("event", event);
        model.addAttribute("guest", guest);
        model.addAttribute("certificates", certificates);
        model.addAttribute("theme", themeService.getEffectiveTheme(guest));
        return "guest-certificates";
    }

    @GetMapping("/invite/{code}/certificates/{resultId}")
    public String certificate(
            @PathVariable String code,
            @PathVariable Long resultId,
            Model model
    ) {
        Guest guest = guestService.getGuestByCode(code).orElse(null);
        if (guest == null) {
            model.addAttribute("message", "Приглашение не найдено");
            model.addAttribute("theme", themeService.defaultTheme());
            return "error";
        }
        Event event = guest.getEvent();
        ActivityResultEntity certificate = activityService.getGuestCertificate(event.getId(), guest.getId(), resultId).orElse(null);
        if (certificate == null) {
            model.addAttribute("message", "Сертификат не найден");
            model.addAttribute("theme", themeService.getEffectiveTheme(guest));
            return "error";
        }
        model.addAttribute("event", event);
        model.addAttribute("guest", guest);
        model.addAttribute("certificate", certificate);
        model.addAttribute("theme", themeService.getEffectiveTheme(guest));
        return "certificate";
    }

    @PostMapping("/invite/{code}/respond")
    public String respond(
            @PathVariable String code,
            @RequestParam RsvpStatus status,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String wish
    ) {
        guestService.respondToInvite(code, status, name, wish);
        return "redirect:/invite/" + code;
    }

    @PostMapping("/wishlist/{itemId}/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleWishlist(
            @PathVariable Long itemId,
            @RequestBody Map<String, Object> body
    ) {
        Long guestId = number(body.get("guestId"));
        if (guestId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "guestId обязателен."
            ));
        }
        return ResponseEntity.ok(eventService.toggleWishlistReservation(itemId, guestId));
    }

    private static Long number(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
