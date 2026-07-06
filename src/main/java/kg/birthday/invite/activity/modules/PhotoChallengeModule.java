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
import java.util.concurrent.ThreadLocalRandom;

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
        return "Гости крутят мем-рулетку и получают уникальную картинку, которую нужно повторить.";
    }

    @Override
    public String getIcon() {
        return "🖼";
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("title", "Мем-челлендж");
        config.put("instructions", "Крути рулетку: тебе выпадет мем-картинка, которую нужно повторить на фото.");
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

        int index = ThreadLocalRandom.current().nextInt(available.size());
        Map<String, Object> meme = available.get(index);
        Map<String, Object> data = baseMemeData("assign", guestId, meme);
        return ActivityResult.success("Тебе выпал мем: " + meme.get("name") + ". Повтори его на фото.", 0, data);
    }

    private ActivityResult submitCaption(Map<String, Object> config, Map<String, Object> action, List<ActivityResultEntity> previousResults) {
        long guestId = number(action.get("guestId"), -1);
        if (guestId < 0) {
            return ActivityResult.error("Гость не найден.");
        }
        if (alreadySubmitted(previousResults, guestId)) {
            return ActivityResult.error("Ты уже отправил(а) результат по своему мему.");
        }
        Optional<Map<String, Object>> assignment = findOwnAssignment(previousResults, guestId);
        if (assignment.isEmpty()) {
            return ActivityResult.error("Сначала прокрути мем-рулетку и получи карточку.");
        }
        String caption = text(action.get("caption"));
        if (caption == null || caption.length() < 3) {
            return ActivityResult.error("Добавьте короткий комментарий минимум из 3 символов.");
        }
        if (caption.length() > 180) {
            return ActivityResult.error("Комментарий должен быть не длиннее 180 символов.");
        }

        Map<String, Object> data = new LinkedHashMap<>(assignment.get());
        data.put("action", "submit");
        data.put("caption", caption);
        data.put("scoreGuestId", guestId);
        return ActivityResult.success("Повтор мема отправлен: " + caption, points(config, "pointsForCaption", 5), data);
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
        data.put("memePrompt", textOrFallback(meme.get("prompt"), "Повтори позу, эмоцию или сцену с картинки"));
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
            return defaultMemes();
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
        meme.put("prompt", textOrFallback(input.get("prompt"), "Повтори позу, эмоцию или сцену с картинки"));
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
                meme("drake-hotline-bling", "Drake Hotline Bling", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/30b1gx.jpg", "#f6c453", "🖼"),
                meme("two-buttons", "Two Buttons", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1g8my4.jpg", "#7bb7ff", "🖼"),
                meme("distracted-boyfriend", "Distracted Boyfriend", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1ur9b0.jpg", "#f28c8c", "🖼"),
                meme("uno-draw-25-cards", "UNO Draw 25 Cards", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/3lmzyx.jpg", "#ff6b6b", "🖼"),
                meme("bernie-i-am-once-again-asking-for-your-support", "Bernie I Am Once Again Asking For Your Support", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/3oevdk.jpg", "#8aa4bd", "🖼"),
                meme("left-exit-12-off-ramp", "Left Exit 12 Off Ramp", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/22bdq6.jpg", "#79b7ff", "🖼"),
                meme("anakin-padme-4-panel", "Anakin Padme 4 Panel", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/5c7lwq.png", "#ffd166", "🖼"),
                meme("epic-handshake", "Epic Handshake", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/28j0te.jpg", "#5db7de", "🖼"),
                meme("always-has-been", "Always Has Been", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/46e43q.png", "#7d6bff", "🖼"),
                meme("running-away-balloon", "Running Away Balloon", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/261o3j.jpg", "#f7a6c1", "🖼"),
                meme("grus-plan", "Gru's Plan", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/26jxvz.jpg", "#f6c453", "🖼"),
                meme("waiting-skeleton", "Waiting Skeleton", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/2fm6x.jpg", "#c7b8a8", "🖼"),
                meme("sad-pablo-escobar", "Sad Pablo Escobar", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1c1uej.jpg", "#8d99ae", "🖼"),
                meme("disaster-girl", "Disaster Girl", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/23ls.jpg", "#ff8b55", "🖼"),
                meme("x-x-everywhere", "X, X Everywhere", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1ihzfe.jpg", "#9f8cff", "🖼"),
                meme("change-my-mind", "Change My Mind", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/24y43o.jpg", "#72c6a1", "🖼"),
                meme("batman-slapping-robin", "Batman Slapping Robin", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/9ehk.jpg", "#dd6b6b", "🖼"),
                meme("woman-yelling-at-cat", "Woman Yelling At Cat", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/345v97.jpg", "#f7a6c1", "🖼"),
                meme("mocking-spongebob", "Mocking Spongebob", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1otk96.jpg", "#ffe45c", "🖼"),
                meme("ancient-aliens", "Ancient Aliens", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/26am.jpg", "#bfa36f", "🖼"),
                meme("trade-offer", "Trade Offer", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/54hjww.jpg", "#5db7de", "🖼"),
                meme("yall-got-any-more-of-that", "Y'all Got Any More Of That", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/21uy0f.jpg", "#4fb3a3", "🖼"),
                meme("expanding-brain", "Expanding Brain", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1jwhww.jpg", "#9f8cff", "🖼"),
                meme("absolute-cinema", "Absolute Cinema", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/8d317n.png", "#2c2825", "🖼"),
                meme("bike-fall", "Bike Fall", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1b42wl.jpg", "#ff9f7a", "🖼"),
                meme("bernie-sanders-once-again-asking", "Bernie Sanders Once Again Asking", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/3pdf2w.png", "#8aa4bd", "🖼"),
                meme("buff-doge-vs-cheems", "Buff Doge vs. Cheems", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/43a45p.png", "#9bcf6f", "🖼"),
                meme("marked-safe-from", "Marked Safe From", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/2odckz.jpg", "#7fc8a9", "🖼"),
                meme("one-does-not-simply", "One Does Not Simply", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1bij.jpg", "#a58b6f", "🖼"),
                meme("empire-state-building-climbers", "Empire State Building climbers", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/avnxpz.png", "#6ec6ff", "🖼"),
                meme("zero-days-without-lenny-simpsons", "0 days without (Lenny, Simpsons)", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/72epa9.png", "#f1b181", "🖼"),
                meme("is-this-a-pigeon", "Is This A Pigeon", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1o00in.jpg", "#8fd3ff", "🖼"),
                meme("mother-ignoring-kid-drowning-in-a-pool", "Mother Ignoring Kid Drowning In A Pool", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/46hhvr.jpg", "#79b7ff", "🖼"),
                meme("tuxedo-winnie-the-pooh", "Tuxedo Winnie The Pooh", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/2ybua0.png", "#f6c453", "🖼"),
                meme("you-guys-are-getting-paid", "You Guys are Getting Paid", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/2xscjb.png", "#c9a84c", "🖼"),
                meme("this-is-fine", "This Is Fine", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/wxica.jpg", "#ff8b55", "🖼"),
                meme("theyre-the-same-picture", "They're The Same Picture", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/2za3u1.jpg", "#8fb3ff", "🖼"),
                meme("squidward-window", "Squidward window", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/145qvv.jpg", "#72c6a1", "🖼"),
                meme("megamind-peeking", "Megamind peeking", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/64sz4u.png", "#7d6bff", "🖼"),
                meme("this-is-where-id-put-my-trophy-if-i-had-one", "This Is Where I'd Put My Trophy If I Had One", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1wz1x.jpg", "#ffd166", "🖼"),
                meme("monkey-puppet", "Monkey Puppet", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/2gnnjh.jpg", "#c08b5c", "🖼"),
                meme("oprah-you-get-a", "Oprah You Get A", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/gtj5t.jpg", "#c07080", "🖼"),
                meme("clown-applying-makeup", "Clown Applying Makeup", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/38el31.jpg", "#f28c8c", "🖼"),
                meme("i-bet-hes-thinking-about-other-women", "I Bet He's Thinking About Other Women", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/1tl71a.jpg", "#b9a7ff", "🖼"),
                meme("boardroom-meeting-suggestion", "Boardroom Meeting Suggestion", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/m78d.jpg", "#5db7ff", "🖼"),
                meme("imagination-spongebob", "Imagination Spongebob", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/3i7p.jpg", "#ffe45c", "🖼"),
                meme("hide-the-pain-harold", "Hide the Pain Harold", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/gk5el.jpg", "#f1b181", "🖼"),
                meme("pawn-stars-best-i-can-do", "Pawn Stars Best I Can Do", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/19vcz0.jpg", "#bfa36f", "🖼"),
                meme("bell-curve", "Bell Curve", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/8tw3vb.png", "#7d9b76", "🖼"),
                meme("where-monkey", "where monkey", "Imgflip", "Повтори позу, эмоцию или композицию этого мема.", "https://i.imgflip.com/58eyvu.png", "#9b7dc0", "🖼")
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
