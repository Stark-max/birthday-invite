package kg.birthday.invite.service;

import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.entity.ThemePreset;
import kg.birthday.invite.repository.EventRepository;
import kg.birthday.invite.repository.GuestRepository;
import kg.birthday.invite.repository.ThemePresetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThemeService {

    private static final String DEFAULT_PRESET = "elegant-gold";

    private final ThemePresetRepository themePresetRepository;
    private final EventRepository eventRepository;
    private final GuestRepository guestRepository;
    private final EventService eventService;

    public Map<String, String> defaultTheme() {
        return new LinkedHashMap<>(Map.ofEntries(
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
                Map.entry("cardShadow", "0 1px 3px rgba(0,0,0,0.04), 0 8px 32px rgba(0,0,0,0.04)"),
                Map.entry("backgroundPattern", "none"),
                Map.entry("backgroundGradient", "radial-gradient(ellipse at 20% 20%, rgba(201,168,76,0.06) 0%, transparent 50%)"),
                Map.entry("headerStyle", "centered"),
                Map.entry("animationStyle", "fadeUp"),
                Map.entry("visualEffect", "birthday")
        ));
    }

    @Transactional(readOnly = true)
    public List<ThemePreset> getAllPresets() {
        List<ThemePreset> presets = new ArrayList<>(themePresetRepository.findAllByBuiltinTrueOrderBySortOrderAsc());
        eventService.findEvent().ifPresent(event -> presets.addAll(themePresetRepository.findAllByEventIdOrderBySortOrderAsc(event.getId())));
        return presets;
    }

    @Transactional(readOnly = true)
    public List<ThemePreset> getAllPresets(Long eventId) {
        List<ThemePreset> presets = new ArrayList<>(themePresetRepository.findAllByBuiltinTrueOrderBySortOrderAsc());
        presets.addAll(themePresetRepository.findAllByEventIdOrderBySortOrderAsc(eventId));
        return presets;
    }

    @Transactional(readOnly = true)
    public Map<String, String> getGlobalTheme() {
        Map<String, String> base = defaultThemeFromPreset();
        return eventService.findEvent()
                .map(Event::getGlobalTheme)
                .filter(theme -> !theme.isEmpty())
                .map(theme -> mergeTheme(base, theme))
                .orElse(base);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getGlobalTheme(Long eventId) {
        Map<String, String> base = defaultThemeFromPreset();
        return eventRepository.findById(eventId)
                .map(Event::getGlobalTheme)
                .filter(theme -> theme != null && !theme.isEmpty())
                .map(theme -> mergeTheme(base, theme))
                .orElse(base);
    }

    public Map<String, String> getGlobalTheme(Event event) {
        Map<String, String> base = defaultThemeFromPreset();
        if (event == null || event.getGlobalTheme() == null || event.getGlobalTheme().isEmpty()) {
            return base;
        }
        return mergeTheme(base, event.getGlobalTheme());
    }

    @Transactional(readOnly = true)
    public Map<String, String> getEffectiveTheme(Guest guest) {
        Map<String, String> theme = defaultThemeFromPreset();
        if (guest == null) {
            return theme;
        }
        if (guest.getEvent() != null && guest.getEvent().getGlobalTheme() != null && !guest.getEvent().getGlobalTheme().isEmpty()) {
            theme = mergeTheme(theme, guest.getEvent().getGlobalTheme());
        }
        if (guest.getPersonalTheme() != null && !guest.getPersonalTheme().isEmpty()) {
            Map<String, String> personalTheme = new LinkedHashMap<>(guest.getPersonalTheme());
            personalTheme.remove("visualEffect");
            theme = mergeTheme(theme, personalTheme);
        }
        return theme;
    }

    @Transactional
    public void setGlobalTheme(Map<String, String> themeData) {
        setGlobalTheme(eventService.getEvent().getId(), themeData);
    }

    @Transactional
    public void setGlobalTheme(Long eventId, Map<String, String> themeData) {
        Event event = eventService.getEvent(eventId);
        event.setGlobalTheme(cleanTheme(themeData));
        eventRepository.save(event);
    }

    @Transactional
    public void setVisualEffect(String visualEffect) {
        setVisualEffect(eventService.getEvent().getId(), visualEffect);
    }

    @Transactional
    public void setVisualEffect(Long eventId, String visualEffect) {
        List<String> allowed = List.of("birthday", "spring", "summer", "sakura", "autumn", "winter");
        String selected = allowed.contains(visualEffect) ? visualEffect : "birthday";
        Event event = eventService.getEvent(eventId);
        Map<String, String> theme = new LinkedHashMap<>();
        if (event.getGlobalTheme() != null) {
            theme.putAll(event.getGlobalTheme());
        }
        theme.put("visualEffect", selected);
        event.setGlobalTheme(cleanTheme(theme));
        eventRepository.save(event);
    }

    @Transactional
    public void setPersonalTheme(Long guestId, Map<String, String> themeData) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found: " + guestId));
        Map<String, String> personalTheme = cleanTheme(themeData);
        personalTheme.remove("visualEffect");
        guest.setPersonalTheme(personalTheme);
        guestRepository.save(guest);
    }

    @Transactional
    public void setPersonalTheme(Long eventId, Long guestId, Map<String, String> themeData) {
        Guest guest = guestRepository.findByIdAndEventId(guestId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found for event: " + guestId));
        Map<String, String> personalTheme = cleanTheme(themeData);
        personalTheme.remove("visualEffect");
        guest.setPersonalTheme(personalTheme);
        guestRepository.save(guest);
    }

    @Transactional
    public void resetPersonalTheme(Long guestId) {
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found: " + guestId));
        guest.setPersonalTheme(new LinkedHashMap<>());
        guestRepository.save(guest);
    }

    @Transactional
    public void resetPersonalTheme(Long eventId, Long guestId) {
        Guest guest = guestRepository.findByIdAndEventId(guestId, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found for event: " + guestId));
        guest.setPersonalTheme(new LinkedHashMap<>());
        guestRepository.save(guest);
    }

    @Transactional
    public void applyPresetToEvent(String presetSlug) {
        ThemePreset preset = findPreset(presetSlug);
        Map<String, String> theme = new LinkedHashMap<>(preset.getThemeData());
        eventService.findEvent()
                .map(Event::getGlobalTheme)
                .map(current -> current.get("visualEffect"))
                .filter(value -> value != null && !value.isBlank())
                .ifPresent(value -> theme.put("visualEffect", value));
        setGlobalTheme(theme);
    }

    @Transactional
    public void applyPresetToEvent(Long eventId, String presetSlug) {
        ThemePreset preset = findPresetForEvent(presetSlug, eventId);
        Map<String, String> theme = new LinkedHashMap<>(preset.getThemeData());
        eventRepository.findById(eventId)
                .map(Event::getGlobalTheme)
                .map(current -> current.get("visualEffect"))
                .filter(value -> value != null && !value.isBlank())
                .ifPresent(value -> theme.put("visualEffect", value));
        setGlobalTheme(eventId, theme);
    }

    @Transactional
    public void applyPresetToGuest(Long guestId, String presetSlug) {
        ThemePreset preset = findPreset(presetSlug);
        setPersonalTheme(guestId, preset.getThemeData());
    }

    @Transactional
    public void applyPresetToGuest(Long eventId, Long guestId, String presetSlug) {
        ThemePreset preset = findPresetForEvent(presetSlug, eventId);
        setPersonalTheme(eventId, guestId, preset.getThemeData());
    }

    @Transactional
    public ThemePreset createCustomPreset(String name, Map<String, String> themeData) {
        return createCustomPreset(eventService.getEvent().getId(), name, themeData);
    }

    @Transactional
    public ThemePreset createCustomPreset(Long eventId, String name, Map<String, String> themeData) {
        Event event = eventService.getEvent(eventId);
        ThemePreset preset = new ThemePreset();
        preset.setName(name);
        preset.setSlug(uniqueSlug(name));
        preset.setDescription("Custom preset");
        preset.setThemeData(cleanTheme(themeData));
        preset.setBuiltin(false);
        preset.setEvent(event);
        preset.setSortOrder(getAllPresets(eventId).size() + 1);
        return themePresetRepository.save(preset);
    }

    @Transactional
    public void deleteCustomPreset(Long id) {
        themePresetRepository.findById(id)
                .filter(preset -> !preset.isBuiltin())
                .ifPresent(themePresetRepository::delete);
    }

    @Transactional
    public void deleteCustomPreset(Long eventId, Long id) {
        themePresetRepository.findByIdAndEventId(id, eventId)
                .filter(preset -> !preset.isBuiltin())
                .ifPresent(themePresetRepository::delete);
    }

    public Map<String, String> mergeTheme(Map<String, String> base, Map<String, String> override) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        if (override != null) {
            override.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    merged.put(key, value);
                }
            });
        }
        return merged;
    }

    public Map<String, String> cleanTheme(Map<String, String> input) {
        return mergeTheme(defaultThemeFromPreset(), input);
    }

    private ThemePreset findPreset(String slug) {
        return themePresetRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Theme preset not found: " + slug));
    }

    private ThemePreset findPresetForEvent(String slug, Long eventId) {
        ThemePreset preset = findPreset(slug);
        if (!preset.isBuiltin() && (preset.getEvent() == null || !eventId.equals(preset.getEvent().getId()))) {
            throw new IllegalArgumentException("Theme preset not found for event: " + slug);
        }
        return preset;
    }

    private Map<String, String> defaultThemeFromPreset() {
        Map<String, String> defaults = defaultTheme();
        return themePresetRepository.findBySlug(DEFAULT_PRESET)
                .map(ThemePreset::getThemeData)
                .filter(theme -> !theme.isEmpty())
                .map(theme -> mergeTheme(defaults, theme))
                .orElse(defaults);
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name == null ? "custom" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "custom";
        }
        String slug = base;
        int suffix = 2;
        while (themePresetRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }
}
