package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class QuizModule implements ActivityModule {

    @Override
    public String getSlug() {
        return "quiz";
    }

    @Override
    public String getDisplayName() {
        return "Викторина";
    }

    @Override
    public String getDescription() {
        return "Вопросы про именинника с подсчетом очков.";
    }

    @Override
    public String getIcon() {
        return "🧠";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        return new LinkedHashMap<>(Map.of(
                "title", "Насколько хорошо ты меня знаешь?",
                "questions", List.of(
                        question("q1", "В каком городе я родился?", "multiple_choice", List.of("Бишкек", "Ош", "Каракол", "Токмок"), 0, 10),
                        question("q2", "Мой любимый фильм?", "multiple_choice", List.of("Inception", "Interstellar", "The Matrix", "Fight Club"), 1, 10),
                        question("q3", "Сколько мне исполняется лет?", "text_input", List.of(), "25", 15)
                ),
                "showCorrectAnswers", true,
                "showLeaderboard", true,
                "allowRetry", false,
                "timeLimitSeconds", 0
        ));
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> questions = questions(config);
        if (questions.isEmpty()) {
            errors.add("Добавьте минимум один вопрос.");
        }
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            if (string(question.get("text")).isBlank()) {
                errors.add("Вопрос " + (i + 1) + ": текст обязателен.");
            }
            if (!question.containsKey("correctAnswer")) {
                errors.add("Вопрос " + (i + 1) + ": правильный ответ обязателен.");
            }
            if ("multiple_choice".equals(question.get("type")) && list(question.get("options")).isEmpty()) {
                errors.add("Вопрос " + (i + 1) + ": варианты ответа обязательны.");
            }
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        boolean allowRetry = bool(config.get("allowRetry"));
        if (!allowRetry && previousResults != null && !previousResults.isEmpty()) {
            return ActivityResult.error("Ты уже проходил(а) эту викторину.");
        }

        Map<String, Object> answers = map(action.getOrDefault("answers", action));
        if (answers.isEmpty()) {
            return ActivityResult.error("Ответы не переданы.");
        }
        int totalPoints = 0;
        int correctCount = 0;
        List<Map<String, Object>> checked = new ArrayList<>();

        for (Map<String, Object> question : questions(config)) {
            String id = string(question.get("id"));
            Object expected = question.get("correctAnswer");
            Object actual = answers.get(id);
            if (actual == null || string(actual).isBlank()) {
                return ActivityResult.error("Ответьте на все вопросы.");
            }
            boolean correct = isCorrect(question, expected, actual);
            int points = number(question.get("points"), 0);
            if (correct) {
                correctCount++;
                totalPoints += points;
            }
            checked.add(new LinkedHashMap<>(Map.of(
                    "questionId", id,
                    "actual", actual == null ? "" : actual,
                    "correct", correct,
                    "points", correct ? points : 0
            )));
        }

        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("type", "quiz");
        resultData.put("correctCount", correctCount);
        resultData.put("totalQuestions", questions(config).size());
        resultData.put("answers", checked);
        return ActivityResult.success("Результат сохранен.", totalPoints, resultData);
    }

    private static Map<String, Object> question(String id, String text, String type, List<String> options, Object correctAnswer, int points) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", id);
        question.put("text", text);
        question.put("type", type);
        question.put("options", options);
        question.put("correctAnswer", correctAnswer);
        question.put("points", points);
        return question;
    }

    private static boolean isCorrect(Map<String, Object> question, Object expected, Object actual) {
        if ("multiple_choice".equals(question.get("type"))) {
            return number(expected, -1) == number(actual, -2);
        }
        return string(expected).trim().toLowerCase(Locale.ROOT)
                .equals(string(actual).trim().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> questions(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("questions");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
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

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
