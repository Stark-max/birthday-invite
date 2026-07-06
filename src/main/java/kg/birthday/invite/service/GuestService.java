package kg.birthday.invite.service;

import kg.birthday.invite.dto.EventStatsResponse;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.enums.RsvpStatus;
import kg.birthday.invite.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GuestService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 8;

    private final GuestRepository guestRepository;
    private final EventService eventService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Guest addGuest(String label) {
        return addGuest(eventService.getEvent().getId(), label);
    }

    @Transactional
    public Guest addGuest(Long eventId, String label) {
        Event event = eventService.getEvent(eventId);
        Guest guest = new Guest();
        guest.setEvent(event);
        guest.setLabel(label == null || label.isBlank() ? "Гость" : label.trim());
        guest.setCode(generateUniqueCode());
        guest.setStatus(RsvpStatus.PENDING);
        return guestRepository.save(guest);
    }

    @Transactional
    public void removeGuest(Long id) {
        guestRepository.deleteById(id);
    }

    @Transactional
    public void removeGuest(Long eventId, Long id) {
        Guest guest = guestRepository.findByIdAndEventId(id, eventId)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found for event: " + id));
        guestRepository.delete(guest);
    }

    @Transactional(readOnly = true)
    public Optional<Guest> getGuestByCode(String code) {
        return guestRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public List<Guest> getAllGuests() {
        return eventService.findEvent()
                .map(event -> guestRepository.findAllByEventId(event.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<Guest> getAllGuests(Long eventId) {
        return guestRepository.findAllByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public List<Guest> getAcceptedGuests(Long eventId) {
        return guestRepository.findAllByEventIdAndStatus(eventId, RsvpStatus.ACCEPTED);
    }

    @Transactional
    public Guest respondToInvite(String code, RsvpStatus status, String name, String wish) {
        Guest guest = guestRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Guest not found by code: " + code));
        guest.setStatus(status);
        guest.setName(trimToNull(name));
        guest.setWish(status == RsvpStatus.DECLINED ? trimToNull(wish) : null);
        guest.setRespondedAt(status == RsvpStatus.PENDING ? null : LocalDateTime.now());
        return guestRepository.save(guest);
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getStats() {
        return eventService.findEvent()
                .map(event -> {
                    long accepted = guestRepository.countByEventIdAndStatus(event.getId(), RsvpStatus.ACCEPTED);
                    long declined = guestRepository.countByEventIdAndStatus(event.getId(), RsvpStatus.DECLINED);
                    long pending = guestRepository.countByEventIdAndStatus(event.getId(), RsvpStatus.PENDING);
                    return new EventStatsResponse(accepted + declined + pending, accepted, declined, pending);
                })
                .orElse(new EventStatsResponse(0, 0, 0, 0));
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getStats(Long eventId) {
        long accepted = guestRepository.countByEventIdAndStatus(eventId, RsvpStatus.ACCEPTED);
        long declined = guestRepository.countByEventIdAndStatus(eventId, RsvpStatus.DECLINED);
        long pending = guestRepository.countByEventIdAndStatus(eventId, RsvpStatus.PENDING);
        return new EventStatsResponse(accepted + declined + pending, accepted, declined, pending);
    }

    public String generateUniqueCode() {
        String code;
        do {
            code = randomCode();
        } while (guestRepository.existsByCode(code));
        return code;
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
