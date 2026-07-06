package kg.birthday.invite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EventSetupRequest {
    @NotBlank
    @Size(min = 3, max = 120)
    private String adminLogin = "superadmin";

    @NotBlank
    private String adminDisplayName = "Super Admin";

    @NotBlank
    private String name;

    @NotNull
    private LocalDate date;

    private String time;
    private String location;
    private String locationUrl;
    private String message;
    private String contactInfo;

    @NotBlank
    @Size(min = 6)
    private String password;

    private List<String> wishlistItems = new ArrayList<>();
}
