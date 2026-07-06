package kg.birthday.invite.repository;

import jakarta.persistence.LockModeType;
import kg.birthday.invite.entity.ActivityInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivityInstanceRepository extends JpaRepository<ActivityInstance, Long> {
    List<ActivityInstance> findAllByEventIdAndEnabledTrueOrderBySortOrderAsc(Long eventId);

    List<ActivityInstance> findAllByEventIdAndEnabledFalseOrderBySortOrderAsc(Long eventId);

    List<ActivityInstance> findAllByEventIdOrderBySortOrderAsc(Long eventId);

    boolean existsByEventIdAndModuleSlugAndEnabledTrue(Long eventId, String moduleSlug);

    Optional<ActivityInstance> findFirstByEventIdAndModuleSlugAndEnabledTrue(Long eventId, String moduleSlug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select activity from ActivityInstance activity where activity.id = :id")
    Optional<ActivityInstance> findByIdForUpdate(@Param("id") Long id);

    Optional<ActivityInstance> findByIdAndEventId(Long id, Long eventId);
}
