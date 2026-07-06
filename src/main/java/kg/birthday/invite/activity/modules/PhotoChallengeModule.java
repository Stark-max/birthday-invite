package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PhotoChallengeModule implements ActivityModule {

    @Override
    public String getSlug() {
        return "photo-challenge";
    }

    @Override
    public String getDisplayName() {
        return "Фото-челлендж";
    }

    @Override
    public String getDescription() {
        return "Текстовые фото-задания без хранения фотографий в приложении.";
    }

    @Override
    public String getIcon() {
        return "📸";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        return new LinkedHashMap<>(Map.of(
                "title", "Фото-челлендж",
                "challenges", List.of(
                        "Сделай самое смешное селфи с именинником",
                        "Повтори детское фото именинника",
                        "Групповое фото в самой безумной позе",
                        "Фото с самым необычным предметом на празднике"
                ),
                "showGallery", true,
                "allowVoting", true
        ));
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        if (strings(config.get("challenges")).isEmpty()) {
            errors.add("Добавьте минимум один челлендж.");
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        List<String> challenges = strings(config.get("challenges"));
        String actionType = String.valueOf(action.getOrDefault("action", "complete"));
        if ("vote".equals(actionType)) {
            return vote(config, action, previousResults);
        }
        if (!"complete".equals(actionType)) {
            return ActivityResult.error("Действие не поддерживается.");
        }

        int index = number(action.get("challengeIndex"), -1);
        if (index < 0 || index >= challenges.size()) {
            return ActivityResult.error("Челлендж не найден.");
        }
        long guestId = number(action.get("guestId"), -1);
        if (alreadyCompleted(previousResults, guestId, index)) {
            return ActivityResult.error("Этот челлендж уже отмечен выполненным.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "photo-challenge");
        data.put("action", actionType);
        data.put("scoreGuestId", guestId);
        data.put("challengeIndex", index);
        data.put("challenge", challenges.get(index));
        return ActivityResult.success("Готово: " + challenges.get(index), 5, data);
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
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

    private ActivityResult vote(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        if (!bool(config.getOrDefault("allowVoting", true))) {
            return ActivityResult.error("Голосование отключено.");
        }
        long voterGuestId = number(action.get("guestId"), -1);
        long targetResultId = number(action.get("targetResultId"), -1);
        ActivityResultEntity target = findCompletedResult(previousResults, targetResultId);
        if (target == null) {
            return ActivityResult.error("Выполненный челлендж не найден.");
        }
        Long targetGuestId = target.getGuest() == null ? null : target.getGuest().getId();
        if (Objects.equals(targetGuestId, voterGuestId)) {
            return ActivityResult.error("За свой челлендж голосовать нельзя.");
        }
        if (alreadyVoted(previousResults, voterGuestId, targetResultId)) {
            return ActivityResult.error("Ты уже голосовал(а) за этот челлендж.");
        }

        Map<String, Object> targetData = target.getResultData();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "photo-challenge");
        data.put("action", "vote");
        data.put("scoreGuestId", targetGuestId);
        data.put("voterGuestId", voterGuestId);
        data.put("targetResultId", targetResultId);
        data.put("challengeIndex", targetData.get("challengeIndex"));
        data.put("challenge", targetData.get("challenge"));
        return ActivityResult.success("+1: " + targetData.get("challenge"), 1, data);
    }

    private static ActivityResultEntity findCompletedResult(List<ActivityResultEntity> previousResults, long targetResultId) {
        if (previousResults == null) {
            return null;
        }
        return previousResults.stream()
                .filter(result -> Objects.equals(result.getId(), targetResultId))
                .filter(result -> "complete".equals(String.valueOf(result.getResultData().get("action"))))
                .findFirst()
                .orElse(null);
    }

    private static boolean alreadyCompleted(List<ActivityResultEntity> previousResults, long guestId, int challengeIndex) {
        if (previousResults == null) {
            return false;
        }
        return previousResults.stream()
                .filter(result -> result.getGuest() != null && Objects.equals(result.getGuest().getId(), guestId))
                .map(ActivityResultEntity::getResultData)
                .anyMatch(data -> "complete".equals(String.valueOf(data.get("action")))
                        && number(data.get("challengeIndex"), -1) == challengeIndex);
    }

    private static boolean alreadyVoted(List<ActivityResultEntity> previousResults, long voterGuestId, long targetResultId) {
        if (previousResults == null) {
            return false;
        }
        return previousResults.stream()
                .map(ActivityResultEntity::getResultData)
                .anyMatch(data -> "vote".equals(String.valueOf(data.get("action")))
                        && number(data.get("voterGuestId"), -1) == voterGuestId
                        && number(data.get("targetResultId"), -1) == targetResultId);
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
