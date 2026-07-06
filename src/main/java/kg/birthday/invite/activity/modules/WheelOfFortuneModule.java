package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class WheelOfFortuneModule implements ActivityModule {

    private final Random random;

    public WheelOfFortuneModule() {
        this(new SecureRandom());
    }

    public WheelOfFortuneModule(Random random) {
        this.random = random;
    }

    @Override
    public String getSlug() {
        return "wheel";
    }

    @Override
    public String getDisplayName() {
        return "Колесо Фортуны";
    }

    @Override
    public String getDescription() {
        return "Сервер выбирает результат с учетом веса сегментов, а браузер только анимирует колесо.";
    }

    @Override
    public String getIcon() {
        return "🎡";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        return new LinkedHashMap<>(Map.of(
                "title", "Крути колесо!",
                "mode", "prizes",
                "segments", List.of(
                        segment("Выбирает тост", "#C9A84C", 1),
                        segment("Танцует первым", "#7D9B76", 1),
                        segment("Рассказывает историю", "#B8704A", 1),
                        segment("Получает десерт первым", "#C07080", 1),
                        segment("Выбирает музыку", "#6B8EAE", 1),
                        segment("Фото с именинником", "#9B7DC0", 1)
                ),
                "spinPerGuest", 1,
                "showHistory", true
        ));
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        if (segments(config).isEmpty()) {
            errors.add("Добавьте минимум один сегмент.");
        }
        if (segments(config).stream().mapToInt(segment -> number(segment.get("weight"), 0)).sum() <= 0) {
            errors.add("Хотя бы один сегмент должен иметь weight больше 0.");
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        int limit = number(config.get("spinPerGuest"), 1);
        int used = previousResults == null ? 0 : previousResults.size();
        if (limit > 0 && used >= limit) {
            return ActivityResult.error("Лимит вращений исчерпан.");
        }

        List<Map<String, Object>> segments = segments(config);
        int totalWeight = segments.stream().mapToInt(segment -> Math.max(0, number(segment.get("weight"), 0))).sum();
        if (segments.isEmpty() || totalWeight <= 0) {
            return ActivityResult.error("Колесо не настроено.");
        }

        int target = random.nextInt(totalWeight);
        int cursor = 0;
        int selectedIndex = 0;
        Map<String, Object> selected = segments.get(0);
        for (int i = 0; i < segments.size(); i++) {
            int weight = Math.max(0, number(segments.get(i).get("weight"), 0));
            if (weight == 0) {
                continue;
            }
            cursor += weight;
            if (target < cursor) {
                selectedIndex = i;
                selected = segments.get(i);
                break;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "wheel");
        data.put("segmentIndex", selectedIndex);
        data.put("segment", selected);
        data.put("text", selected.get("text"));
        return ActivityResult.success(String.valueOf(selected.get("text")), 5, data);
    }

    private static Map<String, Object> segment(String text, String color, int weight) {
        return new LinkedHashMap<>(Map.of("text", text, "color", color, "weight", weight));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> segments(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("segments");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
