package kg.birthday.invite.repository;

import kg.birthday.invite.entity.WishlistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    @EntityGraph(attributePaths = "reservedBy")
    List<WishlistItem> findAllByEventIdOrderBySortOrderAsc(Long eventId);

    Optional<WishlistItem> findByIdAndEventId(Long id, Long eventId);

    void deleteAllByEventId(Long eventId);
}
