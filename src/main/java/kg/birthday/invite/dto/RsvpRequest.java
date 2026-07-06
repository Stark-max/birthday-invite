package kg.birthday.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kg.birthday.invite.enums.RsvpStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RsvpRequest {
    @NotBlank
    private String name;

    private String wish;

    @NotNull
    private RsvpStatus status;
}
