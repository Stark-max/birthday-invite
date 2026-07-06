package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuizModuleTest {

    private final QuizModule module = new QuizModule();

    @Test
    void validatesEmptyQuestions() {
        assertThat(module.validateConfig(Map.of("questions", List.of()))).isNotEmpty();
    }

    @Test
    void calculatesPointsForCorrectAnswers() {
        ActivityResult result = module.processAction(
                module.getDefaultConfig(),
                Map.of("answers", Map.of("q1", 0, "q2", 1, "q3", "25")),
                List.of()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPoints()).isEqualTo(35);
        assertThat(result.getData()).containsEntry("correctCount", 3);
    }

    @Test
    void blocksRetryWhenRetryIsDisabled() {
        ActivityResult result = module.processAction(
                module.getDefaultConfig(),
                Map.of("answers", Map.of()),
                List.of(new ActivityResultEntity())
        );

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void rejectsEmptyAnswersInsteadOfSavingZeroPointResult() {
        ActivityResult result = module.processAction(module.getDefaultConfig(), Map.of("answers", Map.of()), List.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Ответы не переданы.");
    }
}
