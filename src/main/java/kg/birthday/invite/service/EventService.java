package kg.birthday.invite.service;

import kg.birthday.invite.dto.EventSetupRequest;
import kg.birthday.invite.dto.EventProfileRequest;
import kg.birthday.invite.entity.AdminUser;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.entity.WishlistItem;
import kg.birthday.invite.repository.EventRepository;
import kg.birthday.invite.repository.GuestRepository;
import kg.birthday.invite.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final GuestRepository guestRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public Event createEvent(EventSetupRequest request, AdminUser owner) {
        Event event = new Event();
        applyProfile(event, request.getName(), request.getDate(), request.getTime(), request.getLocation(),
                request.getLocationUrl(), request.getMessage(), request.getContactInfo(), true);
        event.setOwnerAdmin(owner);
        event.setPublicSlug(generatePublicSlug());
        event.setPasswordHash(null);
        event.setShowGuestList(true);
        Event saved = eventRepository.save(event);
        updateWishlist(saved.getId(), request.getWishlistItems());
        return saved;
    }

    @Transactional
    public Event createEvent(AdminUser owner, EventProfileRequest request) {
        if (eventRepository.existsByOwnerAdminId(owner.getId())) {
            throw new IllegalStateException("У администратора уже есть приглашение.");
        }
        Event event = new Event();
        applyProfile(event, request.getName(), request.getDate(), request.getTime(), request.getLocation(),
                request.getLocationUrl(), request.getMessage(), request.getContactInfo(), request.isShowGuestList());
        event.setOwnerAdmin(owner);
        event.setPublicSlug(generatePublicSlug());
        event.setPasswordHash(null);
        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Optional<Event> findEvent() {
        return eventRepository.findFirstByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Event getEvent() {
        return findEvent().orElseThrow(() -> new IllegalStateException("Event is not configured"));
    }

    @Transactional(readOnly = true)
    public Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }

    @Transactional(readOnly = true)
    public Optional<Event> findByOwnerAdminId(Long ownerAdminId) {
        return eventRepository.findFirstByOwnerAdminIdOrderByIdAsc(ownerAdminId);
    }

    @Transactional(readOnly = true)
    public Optional<Event> findByPublicSlug(String publicSlug) {
        return eventRepository.findByPublicSlug(publicSlug);
    }

    @Transactional(readOnly = true)
    public List<Event> getAllEvents() {
        return eventRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public long countEvents() {
        return eventRepository.count();
    }

    @Transactional(readOnly = true)
    public boolean hasEvent() {
        return findEvent().isPresent();
    }

    @Transactional
    public Event updateEvent(Event event) {
        return eventRepository.save(event);
    }

    @Transactional
    public Event updateEventProfile(EventProfileRequest request) {
        return updateEventProfile(getEvent().getId(), request);
    }

    @Transactional
    public Event updateEventProfile(Long eventId, EventProfileRequest request) {
        Event event = getEvent(eventId);
        applyProfile(event, request.getName(), request.getDate(), request.getTime(), request.getLocation(),
                request.getLocationUrl(), request.getMessage(), request.getContactInfo(), request.isShowGuestList());
        return eventRepository.save(event);
    }

    @Transactional
    public void toggleGuestList() {
        toggleGuestList(getEvent().getId());
    }

    @Transactional
    public void toggleGuestList(Long eventId) {
        Event event = getEvent(eventId);
        event.setShowGuestList(!event.isShowGuestList());
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public boolean isPasswordValid(String rawPassword) {
        return findEvent()
                .filter(event -> event.getPasswordHash() != null)
                .map(event -> passwordEncoder.matches(rawPassword, event.getPasswordHash()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> getWishlist() {
        return findEvent()
                .map(event -> getWishlist(event.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<WishlistItem> getWishlist(Long eventId) {
        return wishlistItemRepository.findAllByEventIdOrderBySortOrderAsc(eventId);
    }

    @Transactional
    public void updateWishlist(Long eventId, List<String> titles) {
        wishlistItemRepository.deleteAllByEventId(eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        int order = 0;
        for (String rawTitle : titles == null ? List.<String>of() : titles) {
            String title = emptyToNull(rawTitle);
            if (title == null) {
                continue;
            }
            WishlistItem item = new WishlistItem();
            item.setEvent(event);
            item.setTitle(title);
            item.setSortOrder(order++);
            wishlistItemRepository.save(item);
        }
    }

    @Transactional
    public void updateWishlistDetails(
            Long eventId,
            List<String> rawIds,
            List<String> titles,
            List<String> imageUrls,
            List<String> priceLabels,
            List<String> comments,
            List<String> productUrls
    ) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
        Set<Long> retainedIds = new HashSet<>();
        int order = 0;

        for (int index = 0; index < titles.size(); index++) {
            String title = emptyToNull(valueAt(titles, index));
            if (title == null) {
                continue;
            }
            Long itemId = parseId(valueAt(rawIds, index));
            WishlistItem item = itemId == null
                    ? new WishlistItem()
                    : wishlistItemRepository.findByIdAndEventId(itemId, eventId).orElseGet(WishlistItem::new);
            item.setEvent(event);
            item.setTitle(title);
            item.setImageUrl(safeHttpUrl(valueAt(imageUrls, index)));
            item.setPriceLabel(emptyToNull(valueAt(priceLabels, index)));
            item.setComment(emptyToNull(valueAt(comments, index)));
            item.setProductUrl(safeHttpUrl(valueAt(productUrls, index)));
            item.setSortOrder(order++);
            WishlistItem saved = wishlistItemRepository.save(item);
            retainedIds.add(saved.getId());
        }

        wishlistItemRepository.findAllByEventIdOrderBySortOrderAsc(eventId).stream()
                .filter(item -> !retainedIds.contains(item.getId()))
                .forEach(wishlistItemRepository::delete);
    }

    @Transactional
    public Map<String, Object> toggleWishlistReservation(Long itemId, Long guestId) {
        WishlistItem item = wishlistItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Wishlist item not found: " + itemId));
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found: " + guestId));

        if (!Objects.equals(item.getEvent().getId(), guest.getEvent().getId())) {
            return response(false, "Этот подарок не относится к твоему приглашению.", item);
        }

        Guest reservedBy = item.getReservedBy();
        if (reservedBy == null) {
            item.setReservedBy(guest);
            item.setReservedAt(LocalDateTime.now());
            wishlistItemRepository.save(item);
            return response(true, "Подарок закреплён за тобой.", item);
        }
        if (Objects.equals(reservedBy.getId(), guest.getId())) {
            item.setReservedBy(null);
            item.setReservedAt(null);
            wishlistItemRepository.save(item);
            return response(true, "Выбор отменён.", item);
        }
        return response(false, "Этот подарок уже выбрал другой гость.", item);
    }

    private static Map<String, Object> response(boolean success, String message, WishlistItem item) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("success", success);
        data.put("message", message);
        data.put("itemId", item.getId());
        data.put("reserved", item.getReservedBy() != null);
        data.put("reservedByGuestId", item.getReservedBy() == null ? null : item.getReservedBy().getId());
        return data;
    }

    private static String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String valueAt(List<String> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    private static Long parseId(String value) {
        try {
            return emptyToNull(value) == null ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String safeHttpUrl(String value) {
        String url = emptyToNull(value);
        if (url == null) {
            return null;
        }
        String lower = url.toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://") ? url : null;
    }

    private void applyProfile(
            Event event,
            String name,
            java.time.LocalDate date,
            String time,
            String location,
            String locationUrl,
            String message,
            String contactInfo,
            boolean showGuestList
    ) {
        event.setName(name.trim());
        event.setDate(date);
        event.setTime(emptyToNull(time));
        event.setLocation(emptyToNull(location));
        event.setLocationUrl(safeHttpUrl(locationUrl));
        event.setMessage(emptyToNull(message));
        event.setContactInfo(emptyToNull(contactInfo));
        event.setShowGuestList(showGuestList);
    }

    private String generatePublicSlug() {
        String slug;
        do {
            slug = "event-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (eventRepository.existsByPublicSlug(slug));
        return slug;
    }
}
