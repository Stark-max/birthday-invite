package kg.birthday.invite.repository;

import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.enums.RsvpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    Optional<Guest> findByCode(String code);

    Optional<Guest> findByIdAndEventId(Long id, Long eventId);

    boolean existsByCode(String code);

    List<Guest> findAllByEventId(Long eventId);

    List<Guest> findAllByEventIdAndStatus(Long eventId, RsvpStatus status);

    long countByEventIdAndStatus(Long eventId, RsvpStatus status);
}
