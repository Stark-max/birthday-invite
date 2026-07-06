package kg.birthday.invite.repository;

import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityResultRepository extends JpaRepository<ActivityResultEntity, Long> {
    List<ActivityResultEntity> findAllByActivityInstanceIdOrderByCreatedAtDesc(Long activityInstanceId);

    List<ActivityResultEntity> findAllByActivityInstanceIdAndGuestIdOrderByCreatedAtDesc(Long activityInstanceId, Long guestId);

    List<ActivityResultEntity> findAllByGuestIdAndActivityInstanceId(Long guestId, Long activityInstanceId);

    List<ActivityResultEntity> findAllByActivityInstance_Event_Id(Long eventId);

    List<ActivityResultEntity> findAllByActivityInstance_Event_IdAndGuest_IdOrderByCreatedAtDesc(Long eventId, Long guestId);

    boolean existsByActivityInstanceIdAndGuestId(Long activityInstanceId, Long guestId);

    Optional<ActivityResultEntity> findByIdAndActivityInstance_Event_IdAndGuest_Id(Long id, Long eventId, Long guestId);
}
