package kg.birthday.invite.dto;

public record ActivityModuleInfo(
        String slug,
        String name,
        String description,
        String icon,
        String guestFragmentName,
        String adminFragmentName
) {
}
