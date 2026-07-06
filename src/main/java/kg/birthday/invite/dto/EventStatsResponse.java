package kg.birthday.invite.dto;

public record EventStatsResponse(
        long totalGuests,
        long acceptedCount,
        long declinedCount,
        long pendingCount
) {
}
