package kg.birthday.invite.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class ThemeSettings {
    private Map<String, String> values = new LinkedHashMap<>();
}
