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
import java.util.Objects;
import java.util.Random;

@Component
public class TruthOrDareModule implements ActivityModule {

    private final Random random;

    public TruthOrDareModule() {
        this(new SecureRandom());
    }

    TruthOrDareModule(Random random) {
        this.random = random;
    }

    @Override
    public String getSlug() {
        return "truth-or-dare";
    }

    @Override
    public String getDisplayName() {
        return "Правда или действие";
    }

    @Override
    public String getDescription() {
        return "Карточки с вопросами и заданиями, выбранными сервером без повторов для гостя.";
    }

    @Override
    public String getIcon() {
        return "🎭";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        return new LinkedHashMap<>(Map.of(
                "title", "Правда или Действие",
                "truths", List.of(
                        "Какое самое смешное воспоминание с именинником?",
                        "Какой секрет именинника ты знаешь?",
                        "Когда ты в последний раз плакал(а) от смеха?"
                ),
                "dares", List.of(
                        "Позвони случайному контакту и поздравь с днем рождения",
                        "Изобрази именинника за 30 секунд",
                        "Спой куплет любимой песни именинника"
                ),
                "allowGuestAdd", true
        ));
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        if (strings(config.get("truths")).isEmpty()) {
            errors.add("Добавьте минимум один вопрос для правды.");
        }
        if (strings(config.get("dares")).isEmpty()) {
            errors.add("Добавьте минимум одно действие.");
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        String choice = normalizeChoice(action.get("choice"));
        String actionType = String.valueOf(action.getOrDefault("action", "draw"));
        if ("add".equals(actionType)) {
            return addPrompt(config, action, previousResults, choice);
        }

        List<String> pool = new ArrayList<>("dare".equals(choice) ? strings(config.get("dares")) : strings(config.get("truths")));
        pool.addAll(customPrompts(previousResults, choice));
        if (pool.isEmpty()) {
            return ActivityResult.error("Нет доступных карточек.");
        }
        long guestId = number(action.get("guestId"), -1);
        List<String> shown = previousResults == null ? List.of() : previousResults.stream()
                .filter(result -> result.getGuest() != null && Objects.equals(result.getGuest().getId(), guestId))
                .map(ActivityResultEntity::getResultData)
                .filter(data -> "truth-or-dare".equals(String.valueOf(data.get("type"))))
                .filter(data -> choice.equals(String.valueOf(data.get("choice"))))
                .map(data -> String.valueOf(data.getOrDefault("prompt", "")))
                .filter(value -> !value.isBlank())
                .toList();
        List<String> available = pool.stream().filter(item -> !shown.contains(item)).toList();
        if (available.isEmpty()) {
            return ActivityResult.error("Все карточки этого типа уже показаны.");
        }
        String prompt = available.get(random.nextInt(available.size()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "truth-or-dare");
        data.put("choice", choice);
        data.put("prompt", prompt);
        return ActivityResult.success(prompt, 1, data);
    }

    private ActivityResult addPrompt(
            Map<String, Object> config,
            Map<String, Object> action,
            List<ActivityResultEntity> previousResults,
            String choice
    ) {
        if (!bool(config.getOrDefault("allowGuestAdd", false))) {
            return ActivityResult.error("Добавление карточек отключено.");
        }
        String prompt = String.valueOf(action.getOrDefault("prompt", "")).trim();
        if (prompt.isBlank()) {
            return ActivityResult.error("Введите текст карточки.");
        }

        List<String> existing = new ArrayList<>("dare".equals(choice) ? strings(config.get("dares")) : strings(config.get("truths")));
        existing.addAll(customPrompts(previousResults, choice));
        boolean duplicate = existing.stream().anyMatch(item -> item.equalsIgnoreCase(prompt));
        if (duplicate) {
            return ActivityResult.error("Такая карточка уже есть.");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "truth-or-dare-custom");
        data.put("choice", choice);
        data.put("prompt", prompt);
        data.put("guestId", number(action.get("guestId"), -1));
        return ActivityResult.success("Карточка добавлена.", 0, data);
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private static List<String> customPrompts(List<ActivityResultEntity> previousResults, String choice) {
        if (previousResults == null) {
            return List.of();
        }
        return previousResults.stream()
                .map(ActivityResultEntity::getResultData)
                .filter(data -> "truth-or-dare-custom".equals(String.valueOf(data.get("type"))))
                .filter(data -> choice.equals(String.valueOf(data.get("choice"))))
                .map(data -> String.valueOf(data.getOrDefault("prompt", "")).trim())
                .filter(prompt -> !prompt.isBlank())
                .toList();
    }

    private static String normalizeChoice(Object value) {
        return "dare".equals(String.valueOf(value)) ? "dare" : "truth";
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static long number(Object value, long fallback) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
