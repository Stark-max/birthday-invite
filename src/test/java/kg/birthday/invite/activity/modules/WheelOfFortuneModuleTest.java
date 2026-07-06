package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class WheelOfFortuneModuleTest {

    @Test
    void resultIsInsideConfiguredSegments() {
        WheelOfFortuneModule module = new WheelOfFortuneModule(new Random(1));

        ActivityResult result = module.processAction(module.getDefaultConfig(), Map.of(), List.of());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsKey("segmentIndex");
    }

    @Test
    void zeroWeightSegmentNeverWinsWhenOtherSegmentHasWeight() {
        WheelOfFortuneModule module = new WheelOfFortuneModule(new Random(1));
        Map<String, Object> config = new LinkedHashMap<>(module.getDefaultConfig());
        config.put("segments", List.of(
                Map.of("text", "never", "color", "#000000", "weight", 0),
                Map.of("text", "always", "color", "#ffffff", "weight", 1)
        ));

        ActivityResult result = module.processAction(config, Map.of(), List.of());

        assertThat(result.getData()).containsEntry("segmentIndex", 1);
    }

    @Test
    void spinLimitIsEnforced() {
        WheelOfFortuneModule module = new WheelOfFortuneModule(new Random(1));

        ActivityResult result = module.processAction(module.getDefaultConfig(), Map.of(), List.of(new ActivityResultEntity()));

        assertThat(result.isSuccess()).isFalse();
    }
}
