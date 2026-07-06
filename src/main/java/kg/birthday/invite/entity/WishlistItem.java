package kg.birthday.invite.entity;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "wishlist_items")
@Getter
@Setter
@NoArgsConstructor
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String title;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "price_label", length = 120)
    private String priceLabel;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "product_url", columnDefinition = "text")
    private String productUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserved_by_guest_id")
    private Guest reservedBy;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;
}
