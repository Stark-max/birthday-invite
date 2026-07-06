package kg.birthday.invite.activity.registry;

import kg.birthday.invite.activity.modules.PhotoChallengeModule;
import kg.birthday.invite.activity.modules.QuizModule;
import kg.birthday.invite.activity.modules.TruthOrDareModule;
import kg.birthday.invite.activity.modules.WheelOfFortuneModule;
import kg.birthday.invite.activity.modules.GuessGuestModule;
import kg.birthday.invite.activity.modules.CountdownChallengeModule;
import kg.birthday.invite.activity.modules.GuestCertificatesModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityRegistryTest {

    @Test
    void registersAllModulesAndFindsBySlug() {
        ActivityRegistry registry = new ActivityRegistry(List.of(
                new QuizModule(),
                new WheelOfFortuneModule(),
                new TruthOrDareModule(),
                new PhotoChallengeModule(),
                new GuessGuestModule(),
                new CountdownChallengeModule(),
                new GuestCertificatesModule()
        ));

        assertThat(registry.getModules()).hasSize(7);
        assertThat(registry.getBySlug("quiz")).isPresent();
        assertThat(registry.getBySlug("guess-guest")).isPresent();
        assertThat(registry.getBySlug("countdown-challenge")).isPresent();
        assertThat(registry.getBySlug("guest-certificates")).isPresent();
        assertThat(registry.getBySlug("missing")).isEmpty();
    }
}
