package kg.birthday.invite.dto;

import kg.birthday.invite.entity.Guest;
import kg.birthday.invite.enums.RsvpStatus;

import java.time.LocalDateTime;

public record GuestResponse(
        Long id,
        String code,
        String label,
        String name,
        RsvpStatus status,
        String wish,
        LocalDateTime respondedAt
) {
    public static GuestResponse from(Guest guest) {
        return new GuestResponse(
                guest.getId(),
                guest.getCode(),
                guest.getLabel(),
                guest.getName(),
                guest.getStatus(),
                guest.getWish(),
                guest.getRespondedAt()
        );
    }
}
