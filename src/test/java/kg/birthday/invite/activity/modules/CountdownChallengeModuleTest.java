package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CountdownChallengeModuleTest {

    private final CountdownChallengeModule module = new CountdownChallengeModule();

    @Test
    void completesOpenTaskAndAwardsPoints() {
        LocalDate eventDate = LocalDate.now(ZoneId.of("Asia/Bishkek")).plusDays(7);

        ActivityResult result = module.processAction(
                module.getDefaultConfig(),
                Map.of("_eventDate", eventDate.toString(), "type", "complete_task", "taskId", "day-7", "answer", "Добрый"),
                List.of()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPoints()).isEqualTo(5);
        assertThat(result.getData()).containsEntry("taskId", "day-7");
    }

    @Test
    void blocksLockedTask() {
        LocalDate eventDate = LocalDate.now(ZoneId.of("Asia/Bishkek")).plusDays(10);

        ActivityResult result = module.processAction(
                module.getDefaultConfig(),
                Map.of("_eventDate", eventDate.toString(), "type", "complete_task", "taskId", "day-7", "answer", "Добрый"),
                List.of()
        );

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void blocksDuplicateTaskCompletion() {
        LocalDate eventDate = LocalDate.now(ZoneId.of("Asia/Bishkek")).plusDays(7);
        ActivityResultEntity previous = new ActivityResultEntity();
        previous.setResultData(new LinkedHashMap<>(Map.of("type", "countdown-challenge", "taskId", "day-7")));

        ActivityResult result = module.processAction(
                module.getDefaultConfig(),
                Map.of("_eventDate", eventDate.toString(), "type", "complete_task", "taskId", "day-7", "answer", "Добрый"),
                List.of(previous)
        );

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void validatesSecretCodeConfig() {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "secret");
        task.put("dayOffset", 1);
        task.put("title", "Код");
        task.put("description", "Введи код");
        task.put("type", "secret_code");
        task.put("points", 5);
        Map<String, Object> config = new LinkedHashMap<>(module.getDefaultConfig());
        config.put("tasks", List.of(task));

        assertThat(module.validateConfig(config)).anyMatch(error -> error.contains("correctCode"));
    }
}
