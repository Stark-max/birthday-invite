package kg.birthday.invite.service;

import kg.birthday.invite.dto.EventStatsResponse;
import kg.birthday.invite.entity.Event;
import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.enums.RsvpStatus;
import kg.birthday.invite.repository.GuestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

    @Mock
    GuestRepository guestRepository;

    @Mock
    EventService eventService;

    @InjectMocks
    GuestService guestService;

    @Test
    void addGuestGeneratesUniqueEightCharacterCode() {
        Event event = new Event();
        event.setId(1L);
        when(eventService.getEvent()).thenReturn(event);
        when(guestRepository.existsByCode(anyString())).thenReturn(false);
        when(guestRepository.save(any(Guest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Guest guest = guestService.addGuest("Айдана");

        assertThat(guest.getCode()).hasSize(8);
        assertThat(guest.getLabel()).isEqualTo("Айдана");
        assertThat(guest.getStatus()).isEqualTo(RsvpStatus.PENDING);
    }

    @Test
    void respondToInviteUpdatesStatusNameWishAndTimestamp() {
        Guest guest = new Guest();
        guest.setCode("AbCd1234");
        when(guestRepository.findByCode("AbCd1234")).thenReturn(Optional.of(guest));
        when(guestRepository.save(any(Guest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        guestService.respondToInvite("AbCd1234", RsvpStatus.DECLINED, "Нурай", "С праздником");

        ArgumentCaptor<Guest> captor = ArgumentCaptor.forClass(Guest.class);
        verify(guestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(RsvpStatus.DECLINED);
        assertThat(captor.getValue().getName()).isEqualTo("Нурай");
        assertThat(captor.getValue().getWish()).isEqualTo("С праздником");
        assertThat(captor.getValue().getRespondedAt()).isNotNull();
    }

    @Test
    void getStatsCountsAllStatuses() {
        Event event = new Event();
        event.setId(5L);
        when(eventService.findEvent()).thenReturn(Optional.of(event));
        when(guestRepository.countByEventIdAndStatus(5L, RsvpStatus.ACCEPTED)).thenReturn(2L);
        when(guestRepository.countByEventIdAndStatus(5L, RsvpStatus.DECLINED)).thenReturn(1L);
        when(guestRepository.countByEventIdAndStatus(5L, RsvpStatus.PENDING)).thenReturn(3L);

        EventStatsResponse stats = guestService.getStats();

        assertThat(stats.totalGuests()).isEqualTo(6L);
        assertThat(stats.acceptedCount()).isEqualTo(2L);
        assertThat(stats.declinedCount()).isEqualTo(1L);
        assertThat(stats.pendingCount()).isEqualTo(3L);
    }
}
