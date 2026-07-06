package kg.birthday.invite.repository;

import kg.birthday.invite.entity.ActivityInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityInstanceRepository extends JpaRepository<ActivityInstance, Long> {
    List<ActivityInstance> findAllByEventIdAndEnabledTrueOrderBySortOrderAsc(Long eventId);

    List<ActivityInstance> findAllByEventIdAndEnabledFalseOrderBySortOrderAsc(Long eventId);

    List<ActivityInstance> findAllByEventIdOrderBySortOrderAsc(Long eventId);

    boolean existsByEventIdAndModuleSlugAndEnabledTrue(Long eventId, String moduleSlug);

    Optional<ActivityInstance> findByIdAndEventId(Long id, Long eventId);
}
