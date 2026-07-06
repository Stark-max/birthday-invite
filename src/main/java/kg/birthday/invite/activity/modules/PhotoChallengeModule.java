package kg.birthday.invite.activity.modules;

import kg.birthday.invite.activity.ActivityModule;
import kg.birthday.invite.activity.ActivityResult;
import kg.birthday.invite.entity.ActivityResultEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class PhotoChallengeModule implements ActivityModule {

    @Override
    public String getSlug() {
        return "photo-challenge";
    }

    @Override
    public String getDisplayName() {
        return "Мем-челлендж";
    }

    @Override
    public String getDescription() {
        return "Гости получают уникальные мем-шаблоны, придумывают подписи и голосуют за лучшие варианты.";
    }

    @Override
    public String getIcon() {
        return "🖼";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", "Мем-челлендж");
        config.put("instructions", "Получи мем, придумай подпись про праздник и собери голоса гостей.");
        config.put("memes", defaultMemes());
        config.put("showGallery", true);
        config.put("allowVoting", true);
        config.put("pointsForCaption", 5);
        config.put("pointsForVote", 1);
        return config;
    }

    @Override
    public List<String> validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        if (memes(config).isEmpty()) {
            errors.add("Добавьте минимум один мем-шаблон.");
        }
        return errors;
    }

    @Override
    public ActivityResult processAction(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        String actionType = String.valueOf(action.getOrDefault("action", "assign"));
        return switch (actionType) {
            case "assign", "draw" -> assignMeme(config, action, previousResults);
            case "submit", "complete" -> submitCaption(config, action, previousResults);
            case "vote" -> vote(config, action, previousResults);
            default -> ActivityResult.error("Действие не поддерживается.");
        };
    }

    private ActivityResult assignMeme(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        long guestId = number(action.get("guestId"), -1);
        if (guestId < 0) {
            return ActivityResult.error("Гость не найден.");
        }
        if (findOwnAssignment(previousResults, guestId).isPresent()) {
            return ActivityResult.error("Мем уже выдан. Обнови страницу, чтобы увидеть свою карточку.");
        }

        List<Map<String, Object>> memes = memes(config);
        Set<String> usedIds = assignedMemeIds(previousResults);
        List<Map<String, Object>> available = memes.stream()
                .filter(meme -> !usedIds.contains(memeId(meme)))
                .toList();
        if (available.isEmpty()) {
            return ActivityResult.error("Все мемы уже разобрали. Добавьте новые шаблоны в админке.");
        }

        int index = Math.floorMod((int) (guestId * 31 + usedIds.size() * 17), available.size());
        Map<String, Object> meme = available.get(index);
        Map<String, Object> data = baseMemeData("assign", guestId, meme);
        return ActivityResult.success("Тебе выпал мем: " + meme.get("name"), 0, data);
    }

    private ActivityResult submitCaption(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        long guestId = number(action.get("guestId"), -1);
        if (guestId < 0) {
            return ActivityResult.error("Гость не найден.");
        }
        if (alreadySubmitted(previousResults, guestId)) {
            return ActivityResult.error("Ты уже отправил(а) подпись к своему мему.");
        }
        Optional<Map<String, Object>> assignment = findOwnAssignment(previousResults, guestId);
        if (assignment.isEmpty()) {
            return ActivityResult.error("Сначала получи мем-карточку.");
        }
        String caption = text(action.get("caption"));
        if (caption == null || caption.length() < 3) {
            return ActivityResult.error("Добавьте подпись минимум из 3 символов.");
        }
        if (caption.length() > 180) {
            return ActivityResult.error("Подпись должна быть не длиннее 180 символов.");
        }

        Map<String, Object> data = new LinkedHashMap<>(assignment.get());
        data.put("action", "submit");
        data.put("caption", caption);
        data.put("scoreGuestId", guestId);
        return ActivityResult.success("Мем отправлен: " + caption, points(config, "pointsForCaption", 5), data);
    }

    private ActivityResult vote(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        if (!bool(config.getOrDefault("allowVoting", true))) {
            return ActivityResult.error("Голосование отключено.");
        }
        long voterGuestId = number(action.get("guestId"), -1);
        long targetResultId = number(action.get("targetResultId"), -1);
        ActivityResultEntity target = findSubmittedResult(previousResults, targetResultId);
        if (target == null) {
            return ActivityResult.error("Готовый мем не найден.");
        }
        Long targetGuestId = target.getGuest() == null ? null : target.getGuest().getId();
        if (Objects.equals(targetGuestId, voterGuestId)) {
            return ActivityResult.error("За свой мем голосовать нельзя.");
        }
        if (alreadyVoted(previousResults, voterGuestId, targetResultId)) {
            return ActivityResult.error("Ты уже голосовал(а) за этот мем.");
        }

        Map<String, Object> targetData = target.getResultData();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "meme-challenge");
        data.put("legacyType", "photo-challenge");
        data.put("action", "vote");
        data.put("scoreGuestId", targetGuestId);
        data.put("voterGuestId", voterGuestId);
        data.put("targetResultId", targetResultId);
        data.put("memeId", targetData.get("memeId"));
        data.put("memeName", targetData.get("memeName"));
        data.put("caption", targetData.get("caption"));
        return ActivityResult.success("+1 к мему: " + targetData.get("memeName"), points(config, "pointsForVote", 1), data);
    }

    private static Map<String, Object> baseMemeData(String action, long guestId, Map<String, Object> meme) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "meme-challenge");
        data.put("legacyType", "photo-challenge");
        data.put("action", action);
        data.put("scoreGuestId", guestId);
        data.put("memeId", memeId(meme));
        data.put("memeName", textOrFallback(meme.get("name"), "Мем"));
        data.put("memeRegion", textOrFallback(meme.get("region"), "global"));
        data.put("memePrompt", textOrFallback(meme.get("prompt"), "Придумай подпись"));
        data.put("memeImageUrl", textOrFallback(meme.get("imageUrl"), ""));
        data.put("memeAccent", textOrFallback(meme.get("accent"), "#ffd166"));
        data.put("memeEmoji", textOrFallback(meme.get("emoji"), "😂"));
        return data;
    }

    private static Optional<Map<String, Object>> findOwnAssignment(List<ActivityResultEntity> previousResults, long guestId) {
        if (previousResults == null) {
            return Optional.empty();
        }
        return previousResults.stream()
                .filter(result -> result.getGuest() != null && Objects.equals(result.getGuest().getId(), guestId))
                .map(ActivityResultEntity::getResultData)
                .filter(PhotoChallengeModule::isMemeData)
                .filter(data -> "assign".equals(String.valueOf(data.get("action"))))
                .max(Comparator.comparing(data -> String.valueOf(data.getOrDefault("memeId", ""))))
                .map(LinkedHashMap::new);
    }

    private static boolean alreadySubmitted(List<ActivityResultEntity> previousResults, long guestId) {
        if (previousResults == null) {
            return false;
        }
        return previousResults.stream()
                .filter(result -> result.getGuest() != null && Objects.equals(result.getGuest().getId(), guestId))
                .map(ActivityResultEntity::getResultData)
                .filter(PhotoChallengeModule::isMemeData)
                .anyMatch(data -> "submit".equals(String.valueOf(data.get("action"))));
    }

    private static Set<String> assignedMemeIds(List<ActivityResultEntity> previousResults) {
        Set<String> ids = new LinkedHashSet<>();
        if (previousResults == null) {
            return ids;
        }
        previousResults.stream()
                .map(ActivityResultEntity::getResultData)
                .filter(PhotoChallengeModule::isMemeData)
                .filter(data -> "assign".equals(String.valueOf(data.get("action")))
                        || "submit".equals(String.valueOf(data.get("action"))))
                .map(data -> text(data.get("memeId")))
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return ids;
    }

    private static ActivityResultEntity findSubmittedResult(List<ActivityResultEntity> previousResults, long targetResultId) {
        if (previousResults == null) {
            return null;
        }
        return previousResults.stream()
                .filter(result -> Objects.equals(result.getId(), targetResultId))
                .filter(result -> {
                    Map<String, Object> data = result.getResultData();
                    return isMemeData(data) && "submit".equals(String.valueOf(data.get("action")));
                })
                .findFirst()
                .orElse(null);
    }

    private static boolean alreadyVoted(List<ActivityResultEntity> previousResults, long voterGuestId, long targetResultId) {
        if (previousResults == null) {
            return false;
        }
        return previousResults.stream()
                .map(ActivityResultEntity::getResultData)
                .filter(PhotoChallengeModule::isMemeData)
                .anyMatch(data -> "vote".equals(String.valueOf(data.get("action")))
                        && number(data.get("voterGuestId"), -1) == voterGuestId
                        && number(data.get("targetResultId"), -1) == targetResultId);
    }

    private static boolean isMemeData(Map<String, Object> data) {
        return "meme-challenge".equals(String.valueOf(data.get("type")))
                || "photo-challenge".equals(String.valueOf(data.get("legacyType")));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> memes(Map<String, Object> config) {
        Object configured = config.get("memes");
        if (configured instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> normalizeMeme((Map<String, Object>) item))
                    .filter(meme -> text(meme.get("name")) != null)
                    .toList();
        }
        List<String> legacyChallenges = strings(config.get("challenges"));
        if (!legacyChallenges.isEmpty()) {
            List<Map<String, Object>> legacy = new ArrayList<>();
            for (int i = 0; i < legacyChallenges.size(); i++) {
                legacy.add(meme("legacy-" + (i + 1), legacyChallenges.get(i), "legacy", "Придумай подпись", "", "#ffd166", "📸"));
            }
            return legacy;
        }
        return List.of();
    }

    private static Map<String, Object> normalizeMeme(Map<String, Object> input) {
        String name = text(input.get("name"));
        String id = text(input.get("id"));
        Map<String, Object> meme = new LinkedHashMap<>();
        meme.put("id", id == null ? slug(name) : id);
        meme.put("name", name);
        meme.put("region", textOrFallback(input.get("region"), "global"));
        meme.put("prompt", textOrFallback(input.get("prompt"), "Придумай подпись"));
        meme.put("imageUrl", textOrFallback(input.get("imageUrl"), ""));
        meme.put("accent", textOrFallback(input.get("accent"), "#ffd166"));
        meme.put("emoji", textOrFallback(input.get("emoji"), "😂"));
        return meme;
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private static int points(Map<String, Object> config, String key, int fallback) {
        return Math.max(0, number(config.get(key), fallback));
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

    private static String memeId(Map<String, Object> meme) {
        String id = text(meme.get("id"));
        return id == null ? slug(textOrFallback(meme.get("name"), "meme")) : id;
    }

    private static String textOrFallback(Object value, String fallback) {
        String text = text(value);
        return text == null ? fallback : text;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String slug(String value) {
        if (value == null || value.isBlank()) {
            return "meme";
        }
        String slug = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9а-яё]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "meme" : slug;
    }

    private static List<Map<String, Object>> defaultMemes() {
        return List.of(
                meme("drake-hotline-bling", "Drake Hotline Bling", "US", "Что выбирает именинник?", "", "#f6c453", "🎧"),
                meme("distracted-boyfriend", "Distracted Boyfriend", "US", "Кто от чего отвлёкся на вечеринке?", "", "#f28c8c", "👀"),
                meme("two-buttons", "Two Buttons", "US", "Два сложных выбора гостя.", "", "#7bb7ff", "🔘"),
                meme("woman-yelling-at-cat", "Woman Yelling at Cat", "US", "Спор гостя и кота о празднике.", "", "#f7a6c1", "🐱"),
                meme("change-my-mind", "Change My Mind", "US", "Самое спорное мнение про вечеринку.", "", "#72c6a1", "🪧"),
                meme("expanding-brain", "Expanding Brain", "US", "Уровни гениальности гостей.", "", "#9f8cff", "🧠"),
                meme("this-is-fine", "This Is Fine", "US", "Когда всё идёт не по плану, но праздник продолжается.", "", "#ff8b55", "🔥"),
                meme("one-does-not-simply", "One Does Not Simply", "US", "Что нельзя просто так сделать на дне рождения?", "", "#a58b6f", "💍"),
                meme("success-kid", "Success Kid", "US", "Маленькая победа гостя.", "", "#6ec6ff", "🏆"),
                meme("bad-luck-brian", "Bad Luck Brian", "US", "Самый неловкий сценарий вечера.", "", "#dd6b6b", "😬"),
                meme("grumpy-cat", "Grumpy Cat", "US", "Гость, которому всё не нравится.", "", "#b7b0a6", "😾"),
                meme("first-world-problems", "First World Problems", "US", "Драматичная проблема праздника.", "", "#8fb3ff", "💅"),
                meme("ancient-aliens", "Ancient Aliens", "US", "Необъяснимая теория про именинника.", "", "#bfa36f", "👽"),
                meme("mocking-spongebob", "Mocking SpongeBob", "US", "Передразни фразу с вечеринки.", "", "#ffe45c", "🧽"),
                meme("surprised-pikachu", "Surprised Pikachu", "US", "Когда очевидное стало сюрпризом.", "", "#ffd64d", "⚡"),
                meme("roll-safe", "Roll Safe", "US", "Гениальный лайфхак гостя.", "", "#4fb3a3", "☝"),
                meme("is-this-a-pigeon", "Is This a Pigeon?", "US", "Перепутай предмет на празднике.", "", "#8fd3ff", "🦋"),
                meme("hide-the-pain-harold", "Hide the Pain Harold", "US", "Улыбка, когда всё пошло странно.", "", "#f1b181", "🙂"),
                meme("left-exit-12-off-ramp", "Left Exit 12 Off Ramp", "US", "Резкий поворот планов.", "", "#79b7ff", "↩"),
                meme("galaxy-brain", "Galaxy Brain", "US", "Самая космическая идея вечера.", "", "#7d6bff", "🌌"),
                meme("doge", "Doge", "US", "Very party. Such birthday.", "", "#e4bc64", "🐕"),
                meme("chad-vs-virgin", "Chad vs Virgin", "US", "Два типа гостей.", "", "#9bcf6f", "💪"),
                meme("trade-offer", "Trade Offer", "US", "Сделка гостя с именинником.", "", "#5db7de", "🤝"),
                meme("bernie-mittens", "Bernie Mittens", "US", "Гость, который пришёл просто посидеть.", "", "#8aa4bd", "🧤"),
                meme("uno-draw-25", "UNO Draw 25", "US", "Когда легче взять 25 карт.", "", "#ff6b6b", "🃏"),
                meme("zhdun", "Ждун", "CIS", "Кого или чего все ждут на празднике?", "", "#c7b8a8", "⏳"),
                meme("preved-medved", "Превед, медвед", "CIS", "Самое неожиданное приветствие гостя.", "", "#c58c62", "👋"),
                meme("boromir-nelzya-prosto-tak-vzyat", "Нельзя просто так взять и...", "CIS", "Что нельзя просто так взять и сделать?", "", "#8b6f5a", "🛡"),
                meme("vzhuh", "Вжух", "CIS", "Что магически изменилось на вечеринке?", "", "#b57cff", "✨"),
                meme("natalya-morskaya-pehota", "Наталья, морская пехота", "CIS", "Самый боевой тост вечера.", "", "#5b94b8", "⚓"),
                meme("ya-uznayu-ego-iz-tysyachi", "Я узнаю его из тысячи", "CIS", "Как узнать именинника из тысячи?", "", "#f2a65a", "🔎"),
                meme("a-che-vsmysle", "А чё, в смысле?", "CIS", "Реакция на внезапный конкурс.", "", "#f0c987", "🤨"),
                meme("nu-davay-rasskazhi", "Ну давай, расскажи", "CIS", "Когда гость ждёт объяснений.", "", "#b9a7ff", "🧐"),
                meme("eto-fiasko-bratan", "Это фиаско, братан", "CIS", "План, который провалился красиво.", "", "#8ecae6", "🤦"),
                meme("kak-tebe-takoe-ilon-mask", "Как тебе такое, Илон Маск?", "CIS", "Изобретение гостей на празднике.", "", "#9ad3bc", "🚀"),
                meme("zhirno", "Жирно", "CIS", "Самая щедрая идея вечера.", "", "#ffd166", "👌"),
                meme("omsk-bird", "Омская птица", "CIS", "Абсурдный поворот сюжета.", "", "#6d8ea0", "🌀"),
                meme("kot-v-shoke", "Кот в шоке", "CIS", "Реакция кота на конкурс.", "", "#f6b6c8", "🙀"),
                meme("spasibo-kep", "Спасибо, кэп", "CIS", "Самое очевидное наблюдение.", "", "#7fc8a9", "🧢"),
                meme("ruki-bazuki", "Руки-базуки", "CIS", "Сила гостя после торта.", "", "#ff9f7a", "💪"),
                meme("ded-inside", "Дед инсайд", "CIS", "Драматичный внутренний монолог.", "", "#8d99ae", "🖤"),
                meme("kotleta-s-pyureshkoy", "Котлета с пюрешкой", "CIS", "Самое домашнее желание.", "", "#e9c46a", "🍽"),
                meme("chebupeli", "Чебупели", "CIS", "Странный, но важный выбор.", "", "#f4a261", "🥟"),
                meme("shrek-russian", "Шрек в СНГ", "CIS", "Болотная мудрость праздника.", "", "#98c379", "💚"),
                meme("nu-pogodi", "Ну, погоди!", "CIS", "Кто кого догоняет на вечеринке?", "", "#a3d5ff", "🐺"),
                meme("cheburashka", "Чебурашка", "CIS", "Самый милый гость вечера.", "", "#c08b5c", "🧡"),
                meme("leopold", "Ребята, давайте жить дружно", "CIS", "Мирный мем для спорящих гостей.", "", "#f5b971", "☮"),
                meme("masha-medved", "Маша и Медведь", "CIS", "Энергия гостя, которого не остановить.", "", "#ff8fab", "🎀"),
                meme("dobry-vecher", "Добрый вечер", "CIS", "Самое эпичное появление.", "", "#4d96ff", "🌙"),
                meme("normalno-delai", "Нормально делай", "CIS", "Совет организатору от эксперта.", "", "#80ed99", "✅")
        );
    }

    private static Map<String, Object> meme(
            String id,
            String name,
            String region,
            String prompt,
            String imageUrl,
            String accent,
            String emoji
    ) {
        Map<String, Object> meme = new LinkedHashMap<>();
        meme.put("id", id);
        meme.put("name", name);
        meme.put("region", region);
        meme.put("prompt", prompt);
        meme.put("imageUrl", imageUrl);
        meme.put("accent", accent);
        meme.put("emoji", emoji);
        return meme;
    }
}
