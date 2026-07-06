package kg.birthday.invite.service;

import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.entity.ThemePreset;
import kg.birthday.invite.repository.EventRepository;
import kg.birthday.invite.repository.GuestRepository;
import kg.birthday.invite.repository.ThemePresetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThemeServiceTest {

    ThemePresetRepository themePresetRepository;
    EventRepository eventRepository;
    GuestRepository guestRepository;
    EventService eventService;
    ThemeService themeService;

    @BeforeEach
    void setUp() {
        themePresetRepository = mock(ThemePresetRepository.class);
        eventRepository = mock(EventRepository.class);
        guestRepository = mock(GuestRepository.class);
        eventService = mock(EventService.class);
        when(themePresetRepository.findBySlug("elegant-gold")).thenReturn(Optional.empty());
        themeService = new ThemeService(themePresetRepository, eventRepository, guestRepository, eventService);
    }

    @Test
    void effectiveThemeUsesPersonalOverGlobalOverDefault() {
        Event event = new Event();
        event.setGlobalTheme(Map.of(
                "colorAccent", "#111111",
                "visualEffect", "autumn"
        ));
        Guest guest = new Guest();
        guest.setEvent(event);
        guest.setPersonalTheme(Map.of(
                "colorAccent", "#222222",
                "visualEffect", "birthday"
        ));

        Map<String, String> theme = themeService.getEffectiveTheme(guest);

        assertThat(theme.get("colorAccent")).isEqualTo("#222222");
        assertThat(theme.get("visualEffect")).isEqualTo("autumn");
        assertThat(theme.get("colorBg")).isEqualTo("#FBF7F0");
    }

    @Test
    void personalThemeStorageDoesNotKeepVisualEffect() {
        Guest guest = new Guest();
        guest.setId(7L);
        when(guestRepository.findById(7L)).thenReturn(Optional.of(guest));

        themeService.setPersonalTheme(7L, Map.of(
                "colorAccent", "#222222",
                "visualEffect", "winter"
        ));

        assertThat(guest.getPersonalTheme())
                .containsEntry("colorAccent", "#222222")
                .doesNotContainKey("visualEffect");
    }

    @Test
    void mergeThemeKeepsBaseAndOverridesProvidedFields() {
        Map<String, String> merged = themeService.mergeTheme(
                new LinkedHashMap<>(Map.of("a", "1", "b", "2")),
                Map.of("b", "3")
        );

        assertThat(merged).containsEntry("a", "1").containsEntry("b", "3");
    }

    @Test
    void createCustomPresetAttachesCurrentEvent() {
        Event event = new Event();
        event.setId(9L);
        when(eventService.getEvent(9L)).thenReturn(event);
        when(themePresetRepository.findAllByBuiltinTrueOrderBySortOrderAsc()).thenReturn(List.of());
        when(themePresetRepository.findAllByEventIdOrderBySortOrderAsc(9L)).thenReturn(List.of());
        when(themePresetRepository.findBySlug("my-theme")).thenReturn(Optional.empty());
        when(themePresetRepository.save(any(ThemePreset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ThemePreset preset = themeService.createCustomPreset(9L, "My Theme", Map.of("colorAccent", "#123456"));

        assertThat(preset.isBuiltin()).isFalse();
        assertThat(preset.getEvent()).isSameAs(event);
        assertThat(preset.getThemeData()).containsEntry("colorAccent", "#123456");
    }
}
