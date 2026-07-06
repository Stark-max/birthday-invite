package kg.birthday.invite.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import kg.birthday.invite.enums.RsvpStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "guests")
@Getter
@Setter
@NoArgsConstructor
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "guest_name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RsvpStatus status = RsvpStatus.PENDING;

    @Column(columnDefinition = "text")
    private String wish;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Type(JsonType.class)
    @Column(name = "personal_theme", columnDefinition = "jsonb")
    private Map<String, String> personalTheme = new LinkedHashMap<>();

    @Column(name = "allow_theme_switch", nullable = false)
    private boolean allowThemeSwitch;

    @PrePersist
    void prePersist() {
        if (status == null) {
            status = RsvpStatus.PENDING;
        }
    }
}
