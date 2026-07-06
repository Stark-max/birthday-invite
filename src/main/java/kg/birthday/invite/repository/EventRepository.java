package kg.birthday.invite.repository;

import kg.birthday.invite.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findFirstByOrderByIdAsc();

    Optional<Event> findFirstByOwnerAdminIdOrderByIdAsc(Long ownerAdminId);

    boolean existsByOwnerAdminId(Long ownerAdminId);

    Optional<Event> findByPublicSlug(String publicSlug);

    boolean existsByPublicSlug(String publicSlug);

    List<Event> findAllByOrderByCreatedAtAsc();
}
