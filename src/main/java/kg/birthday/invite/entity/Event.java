package kg.birthday.invite.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "event_date", nullable = false)
    private LocalDate date;

    @Column(name = "event_time")
    private String time;

    private String location;

    @Column(name = "location_url")
    private String locationUrl;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "contact_info")
    private String contactInfo;

    @Column(name = "password_hash")
    private String passwordHash;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_admin_id", nullable = false)
    private AdminUser ownerAdmin;

    @Column(name = "public_slug", nullable = false, unique = true, length = 140)
    private String publicSlug;

    @Column(name = "show_guest_list", nullable = false)
    private boolean showGuestList = true;

    @Type(JsonType.class)
    @Column(name = "global_theme", columnDefinition = "jsonb")
    private Map<String, String> globalTheme = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
