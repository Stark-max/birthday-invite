package kg.birthday.invite.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "theme_presets")
@Getter
@Setter
@NoArgsConstructor
public class ThemePreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String description;

    @Type(JsonType.class)
    @Column(name = "theme_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> themeData = new LinkedHashMap<>();

    @Column(name = "is_builtin", nullable = false)
    private boolean builtin = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
