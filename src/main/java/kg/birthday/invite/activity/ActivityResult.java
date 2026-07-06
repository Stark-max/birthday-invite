package kg.birthday.invite.activity;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class ActivityResult {
    private boolean success;
    private String message;
    private int points;
    private Map<String, Object> data = new LinkedHashMap<>();

    public static ActivityResult success(String message, int points, Map<String, Object> data) {
        ActivityResult result = new ActivityResult();
        result.success = true;
        result.message = message;
        result.points = points;
        result.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        return result;
    }

    public static ActivityResult error(String message) {
        ActivityResult result = new ActivityResult();
        result.success = false;
        result.message = message;
        result.points = 0;
        return result;
    }
}
