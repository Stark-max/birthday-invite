package kg.birthday.invite.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class ActivityConfig {
    private String slug;
    private String displayName;
    private Map<String, Object> config = new LinkedHashMap<>();
}
