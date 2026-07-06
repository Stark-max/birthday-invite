package kg.birthday.invite.dto;

import kg.birthday.invite.entity.ActivityInstance;
import kg.birthday.invite.entity.ActivityResultEntity;

import java.util.List;

public record ActivityView(
        ActivityInstance instance,
        ActivityModuleInfo module,
        String configJson,
        String guestFragmentName,
        String adminFragmentName,
        List<ActivityResultEntity> results,
        int participantCount,
        String leaderName
) {
}
