package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GuessGuestModule implements ActivityModule {

    @Override
    public String getSlug() {
        return "guess-guest";
    }

    @Override
    public String getDisplayName() {
        return "Угадай гостя";
    }

    @Override
    public String getDescription() {
        return "Игра, где гости угадывают друг друга по фактам и подсказкам.";
    }

    @Override
    public String getIcon() {
        return "🕵️";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> round = new LinkedHashMap<>();
        round.put("id", "r1");
        round.put("clue", "Этот человек знает именинника со школы.");
        round.put("answerGuestId", null);
        round.put("answerGuestName", "");
        round.put("optionGuestIds", List.of());
        round.put("optionGuestNames", Map.of());
        round.put("points", 10);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", "Угадай гостя");
        config.put("description", "Прочитай подсказку и выбери, о ком идёт речь.");
        config.put("rounds", List.of(round));
        config.put("optionsCount", 4);
        config.put("shuffleOptions", true);
        config.put("showCorrectAnswer", true);
        config.put("showLeaderboard", true);
        config.put("allowRetry", false);
        config.put("useOnlyAcceptedGuests", true);
        return config;
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        if (text(config == null ? null : config.get("title")) == null) {
            errors.add("Название активности обязательно.");
        }
        if (number(config == null ? null : config.get("optionsCount"), 4) < 2) {
            errors.add("Количество вариантов должно быть минимум 2.");
        }
        List<Map<String, Object>> rounds = rounds(config);
        if (rounds.isEmpty()) {
            errors.add("Добавьте минимум одну подсказку.");
        }
        for (int i = 0; i < rounds.size(); i++) {
            Map<String, Object> round = rounds.get(i);
            String prefix = "Подсказка " + (i + 1) + ": ";
            if (text(round.get("id")) == null) {
                errors.add(prefix + "id обязателен.");
            }
            if (text(round.get("clue")) == null) {
                errors.add(prefix + "текст подсказки обязателен.");
            }
            Long answerGuestId = longNumber(round.get("answerGuestId"));
            if (answerGuestId == null) {
                errors.add(prefix + "выберите правильного гостя.");
            }
            if (number(round.get("points"), 0) < 0) {
                errors.add(prefix + "очки не могут быть отрицательными.");
            }
            List<Long> options = longList(round.get("optionGuestIds"));
            if (!options.isEmpty() && answerGuestId != null && !options.contains(answerGuestId)) {
                errors.add(prefix + "правильный гость должен быть среди вариантов ответа.");
            }
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        if (!bool(config.get("allowRetry")) && previousResults != null && !previousResults.isEmpty()) {
            return ActivityResult.error("Ты уже проходил(а) игру «Угадай гостя».");
        }
        Map<String, Object> answers = map(action.get("answers"));
        if (answers.isEmpty()) {
            return ActivityResult.error("Ответы не переданы.");
        }

        List<Map<String, Object>> checkedAnswers = new ArrayList<>();
        int correctAnswers = 0;
        int pointsEarned = 0;
        List<Map<String, Object>> rounds = rounds(config);

        for (Map<String, Object> round : rounds) {
            String roundId = text(round.get("id"));
            Long selectedGuestId = longNumber(answers.get(roundId));
            Long correctGuestId = longNumber(round.get("answerGuestId"));
            if (selectedGuestId == null) {
                return ActivityResult.error("Ответьте на все подсказки.");
            }
            boolean correct = selectedGuestId.equals(correctGuestId);
            int points = Math.max(0, number(round.get("points"), 0));
            if (correct) {
                correctAnswers++;
                pointsEarned += points;
            }

            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("roundId", roundId);
            answer.put("selectedGuestId", selectedGuestId);
            answer.put("correctGuestId", correctGuestId);
            answer.put("correctGuestName", text(round.get("answerGuestName")));
            answer.put("correct", correct);
            answer.put("points", correct ? points : 0);
            checkedAnswers.add(answer);
        }

        int totalQuestions = rounds.size();
        int percent = totalQuestions == 0 ? 0 : Math.round((correctAnswers * 100f) / totalQuestions);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "guess-guest");
        data.put("totalQuestions", totalQuestions);
        data.put("correctAnswers", correctAnswers);
        data.put("percent", percent);
        data.put("answers", checkedAnswers);
        return ActivityResult.success("Ты угадал(а) " + correctAnswers + " из " + totalQuestions + " гостей!", pointsEarned, data);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rounds(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("rounds");
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

    private static List<Long> longList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(GuessGuestModule::longNumber)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        return List.of();
    }

    private static Long longNumber(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            String text = String.valueOf(value).trim();
            return text.isEmpty() || "null".equals(text) ? null : Long.parseLong(text);
        } catch (RuntimeException ignored) {
            return null;
        }
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

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
