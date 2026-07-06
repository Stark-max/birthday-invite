package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuestCertificatesModuleTest {

    private final GuestCertificatesModule module = new GuestCertificatesModule();

    @Test
    void defaultConfigIsValid() {
        assertThat(module.validateConfig(module.getDefaultConfig())).isEmpty();
    }

    @Test
    void validatesActivityLeaderboardRule() {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("slug", "best");
        type.put("title", "Лучший");
        type.put("description", "Описание");
        type.put("source", "activity_leaderboard");
        type.put("rule", Map.of("rank", 1));
        Map<String, Object> config = new LinkedHashMap<>(module.getDefaultConfig());
        config.put("certificateTypes", List.of(type));

        assertThat(module.validateConfig(config)).anyMatch(error -> error.contains("activitySlug"));
    }

    @Test
    void processActionIsAdminOnly() {
        ActivityResult result = module.processAction(module.getDefaultConfig(), Map.of(), List.of());

        assertThat(result.isSuccess()).isFalse();
    }
}
