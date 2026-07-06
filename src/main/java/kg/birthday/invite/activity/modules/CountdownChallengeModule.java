package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class CountdownChallengeModule implements ActivityModule {

    private static final Set<String> TASK_TYPES = Set.of("text", "checkbox", "choice", "secret_code");

    @Override
    public String getSlug() {
        return "countdown-challenge";
    }

    @Override
    public String getDisplayName() {
        return "Countdown Challenge";
    }

    @Override
    public String getDescription() {
        return "Ежедневные мини-задания до дня рождения.";
    }

    @Override
    public String getIcon() {
        return "⏳";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", "Обратный отсчёт до праздника");
        config.put("description", "Каждый день открывается новое задание.");
        config.put("timezone", "Asia/Bishkek");
        config.put("unlockMode", "daily");
        config.put("missedDaysPolicy", "allow_previous");
        config.put("showCountdownTimer", true);
        config.put("showProgress", true);
        config.put("allowLateCompletion", true);
        config.put("tasks", List.of(
                task("day-7", 7, "7 дней до праздника", "Напиши одно слово, которое лучше всего описывает именинника.", "text", List.of(), true, 5, ""),
                task("day-6", 6, "6 дней до праздника", "Предложи песню для вечеринки.", "text", List.of(), true, 5, ""),
                task("day-5", 5, "5 дней до праздника", "Выбери настроение праздника.", "choice", List.of("Весело", "Душевно", "Шумно", "Элегантно"), true, 5, "")
        ));
        return config;
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        if (text(config == null ? null : config.get("title")) == null) {
            errors.add("Название Countdown Challenge обязательно.");
        }
        List<Map<String, Object>> tasks = tasks(config);
        if (tasks.isEmpty()) {
            errors.add("Добавьте минимум одно задание.");
        }
        for (int i = 0; i < tasks.size(); i++) {
            Map<String, Object> task = tasks.get(i);
            String prefix = "Задание " + (i + 1) + ": ";
            String type = textOrDefault(task.get("type"), "text");
            if (text(task.get("id")) == null) {
                errors.add(prefix + "id обязателен.");
            }
            if (text(task.get("title")) == null) {
                errors.add(prefix + "заголовок обязателен.");
            }
            if (text(task.get("description")) == null) {
                errors.add(prefix + "описание обязательно.");
            }
            if (number(task.get("dayOffset"), 0) < 0) {
                errors.add(prefix + "dayOffset должен быть >= 0.");
            }
            if (number(task.get("points"), 0) < 0) {
                errors.add(prefix + "очки не могут быть отрицательными.");
            }
            if (!TASK_TYPES.contains(type)) {
                errors.add(prefix + "тип должен быть text, checkbox, choice или secret_code.");
            }
            if ("choice".equals(type) && strings(task.get("options")).isEmpty()) {
                errors.add(prefix + "для choice нужны варианты.");
            }
            if ("secret_code".equals(type) && text(task.get("correctCode")) == null) {
                errors.add(prefix + "для secret_code нужен correctCode.");
            }
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        String type = textOrDefault(action.getOrDefault("type", action.get("action")), "complete_task");
        if (!"complete_task".equals(type)) {
            return ActivityResult.error("Действие не поддерживается.");
        }
        String taskId = text(action.get("taskId"));
        if (taskId == null) {
            return ActivityResult.error("taskId обязателен.");
        }
        Map<String, Object> task = tasks(config).stream()
                .filter(item -> taskId.equals(text(item.get("id"))))
                .findFirst()
                .orElse(null);
        if (task == null) {
            return ActivityResult.error("Задание не найдено.");
        }
        if (alreadyCompleted(previousResults, taskId)) {
            return ActivityResult.error("Это задание уже выполнено.");
        }
        LocalDate eventDate = parseDate(action.get("_eventDate"));
        if (eventDate == null) {
            return ActivityResult.error("Дата события недоступна.");
        }
        ZoneId zone = zone(config.get("timezone"));
        LocalDate today = LocalDate.now(zone);
        if (!isOpen(config, task, eventDate, today)) {
            return ActivityResult.error("Это задание ещё не открыто или уже пропущено.");
        }

        String taskType = textOrDefault(task.get("type"), "text");
        Object answer = action.get("answer");
        if ("checkbox".equals(taskType)) {
            if (bool(task.get("required")) && !bool(action.get("completed"))) {
                return ActivityResult.error("Подтвердите выполнение задания.");
            }
            answer = bool(action.get("completed"));
        } else if (bool(task.get("required")) && text(answer) == null) {
            return ActivityResult.error("Ответ обязателен.");
        }
        if ("choice".equals(taskType) && text(answer) != null && !strings(task.get("options")).contains(String.valueOf(answer))) {
            return ActivityResult.error("Выберите один из доступных вариантов.");
        }
        if ("secret_code".equals(taskType)) {
            String expected = text(task.get("correctCode"));
            if (expected == null || !expected.equalsIgnoreCase(textOrDefault(answer, ""))) {
                return ActivityResult.error("Секретный код неверный.");
            }
        }

        int points = Math.max(0, number(task.get("points"), 0));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "countdown-challenge");
        data.put("action", "complete_task");
        data.put("taskId", taskId);
        data.put("taskTitle", textOrDefault(task.get("title"), taskId));
        data.put("completed", true);
        data.put("completedAt", LocalDateTime.now(zone).toString());
        data.put("answer", answer == null ? "" : answer);
        data.put("points", points);
        return ActivityResult.success("Задание выполнено! +" + points + " очков", points, data);
    }

    private static boolean isOpen(Map<String, Object> config, Map<String, Object> task, LocalDate eventDate, LocalDate today) {
        if ("all_at_once".equals(text(config.get("unlockMode")))) {
            return true;
        }
        int dayOffset = Math.max(0, number(task.get("dayOffset"), 0));
        LocalDate unlockDate = eventDate.minusDays(dayOffset);
        if (today.isBefore(unlockDate)) {
            return false;
        }
        boolean allowLate = bool(config.getOrDefault("allowLateCompletion", true))
                && !"current_only".equals(text(config.get("missedDaysPolicy")));
        return allowLate || today.equals(unlockDate);
    }

    private static boolean alreadyCompleted(List<ActivityResultEntity> previousResults, String taskId) {
        if (previousResults == null) {
            return false;
        }
        return previousResults.stream()
                .map(ActivityResultEntity::getResultData)
                .anyMatch(data -> "countdown-challenge".equals(String.valueOf(data.get("type")))
                        && taskId.equals(String.valueOf(data.get("taskId"))));
    }

    private static Map<String, Object> task(
            String id,
            int dayOffset,
            String title,
            String description,
            String type,
            List<String> options,
            boolean required,
            int points,
            String correctCode
    ) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", id);
        task.put("dayOffset", dayOffset);
        task.put("title", title);
        task.put("description", description);
        task.put("type", type);
        task.put("options", options);
        task.put("required", required);
        task.put("points", points);
        if (correctCode != null && !correctCode.isBlank()) {
            task.put("correctCode", correctCode);
        }
        return task;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> tasks(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("tasks");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private static LocalDate parseDate(Object value) {
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ZoneId zone(Object value) {
        try {
            return ZoneId.of(textOrDefault(value, "Asia/Bishkek"));
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Bishkek");
        }
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
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

    private static String textOrDefault(Object value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text.toLowerCase(Locale.ROOT)) ? null : text;
    }
}
