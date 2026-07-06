package kg.birthday.invite.activity;

import kg.birthday.invite.entity.ActivityResultEntity;

import java.util.List;
import java.util.Map;

public interface ActivityModule {
    String getSlug();

    String getDisplayName();

    String getDescription();

    String getIcon();

    Map<String, Object> getDefaultConfig();

    List<String> validateConfig(Map<String, Object> config);

    ActivityResult processAction(
            Map<String, Object> config,
            Map<String, Object> action,
            List<ActivityResultEntity> previousResults
    );

    default String getGuestFragmentName() {
        return "fragments/activity/" + getSlug() + "-guest";
    }

    default String getAdminFragmentName() {
        return "fragments/activity/" + getSlug() + "-admin";
    }
}
