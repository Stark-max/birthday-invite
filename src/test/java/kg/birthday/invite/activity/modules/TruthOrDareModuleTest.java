package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import kg.birthday.invite.entity.Guest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TruthOrDareModuleTest {

    @Test
    void addsGuestPromptWhenAllowed() {
        TruthOrDareModule module = new TruthOrDareModule(new Random(1));

        ActivityResult result = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "add", "choice", "truth", "prompt", "Custom truth"),
                List.of()
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPoints()).isZero();
        assertThat(result.getData()).containsEntry("type", "truth-or-dare-custom");
        assertThat(result.getData()).containsEntry("prompt", "Custom truth");
    }

    @Test
    void customPromptIsAvailableToOtherGuests() {
        TruthOrDareModule module = new TruthOrDareModule(new Random(1));
        ActivityResultEntity customPrompt = result(
                2L,
                Map.of("type", "truth-or-dare-custom", "choice", "truth", "prompt", "Custom truth")
        );

        ActivityResult result = module.processAction(
                Map.of("truths", List.of(), "dares", List.of(), "allowGuestAdd", true),
                Map.of("guestId", 1, "choice", "truth"),
                List.of(customPrompt)
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Custom truth");
    }

    @Test
    void doesNotRepeatShownPromptForSameGuestWhenAnotherPromptExists() {
        TruthOrDareModule module = new TruthOrDareModule(new Random(1));
        ActivityResultEntity shown = result(
                1L,
                Map.of("type", "truth-or-dare", "choice", "truth", "prompt", "A")
        );

        ActivityResult result = module.processAction(
                Map.of("truths", List.of("A", "B"), "dares", List.of("D"), "allowGuestAdd", true),
                Map.of("guestId", 1, "choice", "truth"),
                List.of(shown)
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("B");
    }

    @Test
    void returnsErrorWhenAllPromptsForChoiceWereShownToGuest() {
        TruthOrDareModule module = new TruthOrDareModule(new Random(1));
        ActivityResultEntity firstShown = result(
                1L,
                Map.of("type", "truth-or-dare", "choice", "truth", "prompt", "A")
        );
        ActivityResultEntity secondShown = result(
                1L,
                Map.of("type", "truth-or-dare", "choice", "truth", "prompt", "B")
        );

        ActivityResult result = module.processAction(
                Map.of("truths", List.of("A", "B"), "dares", List.of("D"), "allowGuestAdd", true),
                Map.of("guestId", 1, "choice", "truth"),
                List.of(firstShown, secondShown)
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Все карточки этого типа уже показаны.");
    }

    @Test
    void rejectsDuplicateGuestPrompt() {
        TruthOrDareModule module = new TruthOrDareModule(new Random(1));
        ActivityResultEntity customPrompt = result(
                2L,
                Map.of("type", "truth-or-dare-custom", "choice", "truth", "prompt", "Custom truth")
        );

        ActivityResult result = module.processAction(
                module.getDefaultConfig(),
                Map.of("guestId", 1, "action", "add", "choice", "truth", "prompt", "custom truth"),
                List.of(customPrompt)
        );

        assertThat(result.isSuccess()).isFalse();
    }

    private static ActivityResultEntity result(Long guestId, Map<String, Object> data) {
        Guest guest = new Guest();
        guest.setId(guestId);

        ActivityResultEntity result = new ActivityResultEntity();
        result.setGuest(guest);
        result.setResultData(new LinkedHashMap<>(data));
        return result;
    }
}
