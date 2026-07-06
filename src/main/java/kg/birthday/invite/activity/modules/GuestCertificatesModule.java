package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GuestCertificatesModule implements ActivityModule {

    private static final Set<String> SOURCES = Set.of("manual", "activity_leaderboard", "total_leaderboard", "custom_result");

    @Override
    public String getSlug() {
        return "guest-certificates";
    }

    @Override
    public String getDisplayName() {
        return "Сертификаты гостей";
    }

    @Override
    public String getDescription() {
        return "Генерация шуточных сертификатов для гостей после праздника.";
    }

    @Override
    public String getIcon() {
        return "🏆";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", "Сертификаты гостей");
        config.put("description", "Памятные награды для гостей праздника.");
        config.put("template", "elegant-gold");
        config.put("allowGuestDownload", true);
        config.put("allowGuestShare", true);
        config.put("showOnGuestPage", true);
        config.put("autoGenerateEnabled", true);
        config.put("certificateTypes", List.of(
                certificateType("best_quiz_player", "Знаток именинника", "За лучший результат в викторине", "activity_leaderboard", Map.of("activitySlug", "quiz", "rank", 1)),
                certificateType("most_active_guest", "Самый активный гость", "За наибольшее количество очков во всех активностях", "total_leaderboard", Map.of("rank", 1)),
                certificateType("best_dancer", "Лучший танцор", "Назначается вручную админом", "manual", Map.of())
        ));
        config.put("certificateText", "Настоящий сертификат подтверждает, что {guestName} получает звание «{certificateTitle}» на дне рождения {eventName}.");
        config.put("footerText", "Спасибо, что был(а) частью этого дня!");
        return config;
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        if (text(config == null ? null : config.get("title")) == null) {
            errors.add("Название сертификатов обязательно.");
        }
        List<Map<String, Object>> types = certificateTypes(config);
        if (types.isEmpty()) {
            errors.add("Добавьте минимум один тип сертификата.");
        }
        for (int i = 0; i < types.size(); i++) {
            Map<String, Object> type = types.get(i);
            String prefix = "Сертификат " + (i + 1) + ": ";
            String source = text(type.get("source"));
            if (text(type.get("slug")) == null) {
                errors.add(prefix + "slug обязателен.");
            }
            if (text(type.get("title")) == null) {
                errors.add(prefix + "название обязательно.");
            }
            if (!SOURCES.contains(source)) {
                errors.add(prefix + "source должен быть manual, activity_leaderboard, total_leaderboard или custom_result.");
            }
            Map<String, Object> rule = map(type.get("rule"));
            if ("activity_leaderboard".equals(source) && text(rule.get("activitySlug")) == null) {
                errors.add(prefix + "для activity_leaderboard нужен rule.activitySlug.");
            }
            if (("activity_leaderboard".equals(source) || "total_leaderboard".equals(source))
                    && number(rule.get("rank"), 1) < 1) {
                errors.add(prefix + "rank должен быть >= 1.");
            }
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        return ActivityResult.error("Сертификаты выдаются только через админку.");
    }

    public static List<Map<String, Object>> certificateTypes(Map<String, Object> config) {
        Object raw = config == null ? null : config.get("certificateTypes");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapped = (Map<String, Object>) item;
                        return mapped;
                    })
                    .toList();
        }
        return List.of();
    }

    private static Map<String, Object> certificateType(String slug, String title, String description, String source, Map<String, Object> rule) {
        Map<String, Object> type = new LinkedHashMap<>();
        type.put("slug", slug);
        type.put("title", title);
        type.put("description", description);
        type.put("source", source);
        type.put("rule", rule);
        return type;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
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

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
