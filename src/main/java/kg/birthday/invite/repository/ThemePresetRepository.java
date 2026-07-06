package kg.birthday.invite.repository;

import kg.birthday.invite.entity.ThemePreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThemePresetRepository extends JpaRepository<ThemePreset, Long> {
    List<ThemePreset> findAllByBuiltinTrueOrderBySortOrderAsc();

    List<ThemePreset> findAllByEventIdOrderBySortOrderAsc(Long eventId);

    Optional<ThemePreset> findBySlug(String slug);

    Optional<ThemePreset> findBySlugAndEventId(String slug, Long eventId);

    Optional<ThemePreset> findByIdAndEventId(Long id, Long eventId);
}
