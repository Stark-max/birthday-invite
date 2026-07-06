package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GuessGuestModuleTest {

    private final GuessGuestModule module = new GuessGuestModule();

    @Test
    void validatesMissingAnswerGuest() {
        Map<String, Object> config = new LinkedHashMap<>(module.getDefaultConfig());

        assertThat(module.validateConfig(config)).anyMatch(error -> error.contains("правильного гостя"));
    }

    @Test
    void calculatesPointsAndAnswerStats() {
        ActivityResult result = module.processAction(
                config(),
                Map.of("answers", Map.of("r1", 12, "r2", 8)),
                List.of()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPoints()).isEqualTo(10);
        assertThat(result.getData()).containsEntry("totalQuestions", 2);
        assertThat(result.getData()).containsEntry("correctAnswers", 1);
        assertThat(result.getData()).containsEntry("percent", 50);
        assertThat((List<?>) result.getData().get("answers")).hasSize(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstAnswer = (Map<String, Object>) ((List<?>) result.getData().get("answers")).get(0);
        assertThat(firstAnswer).containsEntry("roundId", "r1");
        assertThat(firstAnswer).containsEntry("correct", true);
        assertThat(firstAnswer).containsEntry("selectedGuestId", 12L);
        assertThat(firstAnswer).containsEntry("correctGuestId", 12L);
    }

    @Test
    void blocksRetryWhenRetryDisabled() {
        ActivityResult result = module.processAction(
                config(),
                Map.of("answers", Map.of("r1", 12, "r2", 9)),
                List.of(new ActivityResultEntity())
        );

        assertThat(result.isSuccess()).isFalse();
    }

    private static Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", "Угадай гостя");
        config.put("description", "Подсказки");
        config.put("optionsCount", 4);
        config.put("shuffleOptions", true);
        config.put("showCorrectAnswer", true);
        config.put("showLeaderboard", true);
        config.put("allowRetry", false);
        config.put("useOnlyAcceptedGuests", true);
        config.put("rounds", List.of(
                round("r1", "Со школы", 12L, List.of(12L, 8L), 10),
                round("r2", "Любит танцы", 9L, List.of(9L, 8L), 10)
        ));
        return config;
    }

    private static Map<String, Object> round(String id, String clue, Long answer, List<Long> options, int points) {
        Map<String, Object> names = new LinkedHashMap<>();
        for (Long option : options) {
            names.put(String.valueOf(option), "Гость " + option);
        }
        Map<String, Object> round = new LinkedHashMap<>();
        round.put("id", id);
        round.put("clue", clue);
        round.put("answerGuestId", answer);
        round.put("answerGuestName", "Гость " + answer);
        round.put("optionGuestIds", options);
        round.put("optionGuestNames", names);
        round.put("points", points);
        return round;
    }
}
