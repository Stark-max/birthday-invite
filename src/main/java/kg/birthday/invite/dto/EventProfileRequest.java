package kg.birthday.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EventProfileRequest {

    @NotBlank
    private String name;

    @NotNull
    private LocalDate date;

    private String time;
    private String location;
    private String locationUrl;
    private String message;
    private String contactInfo;
    private boolean showGuestList;
}
