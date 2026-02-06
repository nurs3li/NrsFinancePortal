package com.nurseli.nrsfinanceportal.domain.whale;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "whale_history")
public class WhaleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Whale seviyesi USER bazlıdır
     * Account veya Transaction’a bağlı değildir
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "whale_level", nullable = false)
    private WhaleLevel whaleLevel;

    /**
     * Whale impact score (0–100)
     * Decision-support metric
     */
    @Column(name = "impact_score")
    private Integer impactScore;

    /**
     * AUTO_ALERT / MANUAL_REVIEW / SYSTEM_RULE vb.
     */
    @Column(name = "reason")
    private String reason;

    /**
     * Whale detection anı
     * (event time)
     */
    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    /**
     * DB insert zamanı
     * (audit / ordering fallback)
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WhaleHistory() {
        // JPA
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /* ======================
       FACTORY
       ====================== */
    public static WhaleHistory of(
            Long userId,
            WhaleLevel whaleLevel,
            Integer impactScore,
            String reason,
            Instant triggeredAt
    ) {
        WhaleHistory h = new WhaleHistory();
        h.userId = userId;
        h.whaleLevel = whaleLevel;
        h.impactScore = impactScore;
        h.reason = reason;
        h.triggeredAt = triggeredAt;
        return h;
    }
}
