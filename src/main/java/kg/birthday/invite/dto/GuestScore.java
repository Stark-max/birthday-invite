package kg.birthday.invite.dto;

import java.util.Map;

public record GuestScore(
        Long guestId,
        String guestName,
        int totalPoints,
        Map<String, Integer> pointsByActivity
) {
}
