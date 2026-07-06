package kg.birthday.invite.service;

import kg.birthday.invite.dto.EventProfileRequest;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.entity.WishlistItem;
import kg.birthday.invite.repository.EventRepository;
import kg.birthday.invite.repository.GuestRepository;
import kg.birthday.invite.repository.WishlistItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventServiceTest {

    @Test
    void isPasswordValidUsesBCryptHash() {
        EventRepository eventRepository = mock(EventRepository.class);
        WishlistItemRepository wishlistRepository = mock(WishlistItemRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Event event = new Event();
        event.setPasswordHash(encoder.encode("secret"));
        when(eventRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(event));

        EventService service = new EventService(eventRepository, wishlistRepository, guestRepository, encoder);

        assertThat(service.isPasswordValid("secret")).isTrue();
        assertThat(service.isPasswordValid("wrong")).isFalse();
    }

    @Test
    void toggleWishlistReservationClaimsAndReleasesOwnGift() {
        EventRepository eventRepository = mock(EventRepository.class);
        WishlistItemRepository wishlistRepository = mock(WishlistItemRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        Event event = new Event();
        event.setId(1L);
        Guest guest = new Guest();
        guest.setId(2L);
        guest.setEvent(event);
        WishlistItem item = new WishlistItem();
        item.setId(3L);
        item.setEvent(event);
        item.setTitle("Cake");

        when(wishlistRepository.findById(3L)).thenReturn(Optional.of(item));
        when(guestRepository.findById(2L)).thenReturn(Optional.of(guest));
        when(wishlistRepository.save(any(WishlistItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventService service = new EventService(eventRepository, wishlistRepository, guestRepository, encoder);

        Map<String, Object> claimed = service.toggleWishlistReservation(3L, 2L);
        Map<String, Object> released = service.toggleWishlistReservation(3L, 2L);

        assertThat(claimed).containsEntry("success", true);
        assertThat(claimed).containsEntry("reserved", true);
        assertThat(claimed).containsEntry("reservedByGuestId", 2L);
        assertThat(released).containsEntry("success", true);
        assertThat(released).containsEntry("reserved", false);
        verify(wishlistRepository, times(2)).save(item);
    }

    @Test
    void updateWishlistDetailsKeepsReservationAndUpdatesCardFields() {
        EventRepository eventRepository = mock(EventRepository.class);
        WishlistItemRepository wishlistRepository = mock(WishlistItemRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        Event event = new Event();
        event.setId(1L);
        Guest guest = new Guest();
        guest.setId(2L);
        WishlistItem item = new WishlistItem();
        item.setId(3L);
        item.setEvent(event);
        item.setTitle("Old title");
        item.setReservedBy(guest);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(wishlistRepository.findByIdAndEventId(3L, 1L)).thenReturn(Optional.of(item));
        when(wishlistRepository.save(any(WishlistItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wishlistRepository.findAllByEventIdOrderBySortOrderAsc(1L)).thenReturn(List.of(item));

        EventService service = new EventService(eventRepository, wishlistRepository, guestRepository, encoder);
        service.updateWishlistDetails(
                1L,
                List.of("3"),
                List.of("Instant camera"),
                List.of("https://example.com/camera.jpg"),
                List.of("12 000 som"),
                List.of("White color"),
                List.of("https://example.com/camera")
        );

        assertThat(item.getTitle()).isEqualTo("Instant camera");
        assertThat(item.getImageUrl()).isEqualTo("https://example.com/camera.jpg");
        assertThat(item.getPriceLabel()).isEqualTo("12 000 som");
        assertThat(item.getComment()).isEqualTo("White color");
        assertThat(item.getProductUrl()).isEqualTo("https://example.com/camera");
        assertThat(item.getReservedBy()).isSameAs(guest);
        verify(wishlistRepository).save(item);
    }

    @Test
    void updateEventProfileChangesOnlyEventPublicFields() {
        EventRepository eventRepository = mock(EventRepository.class);
        WishlistItemRepository wishlistRepository = mock(WishlistItemRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        Event event = new Event();
        event.setId(1L);
        event.setName("Old name");
        event.setDate(LocalDate.of(2026, 6, 30));
        event.setPasswordHash("hash");
        event.setShowGuestList(true);

        EventProfileRequest request = new EventProfileRequest();
        request.setName("New name");
        request.setDate(LocalDate.of(2026, 7, 1));
        request.setTime("19:30");
        request.setLocation("Garden");
        request.setLocationUrl("https://maps.example/place");
        request.setMessage("Welcome");
        request.setContactInfo("@host");
        request.setShowGuestList(false);

        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventService service = new EventService(eventRepository, wishlistRepository, guestRepository, encoder);
        Event updated = service.updateEventProfile(1L, request);

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.getDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(updated.getTime()).isEqualTo("19:30");
        assertThat(updated.getLocation()).isEqualTo("Garden");
        assertThat(updated.getLocationUrl()).isEqualTo("https://maps.example/place");
        assertThat(updated.getMessage()).isEqualTo("Welcome");
        assertThat(updated.getContactInfo()).isEqualTo("@host");
        assertThat(updated.isShowGuestList()).isFalse();
        assertThat(updated.getPasswordHash()).isEqualTo("hash");
        verify(eventRepository).save(event);
        verifyNoInteractions(wishlistRepository, guestRepository);
    }
}
