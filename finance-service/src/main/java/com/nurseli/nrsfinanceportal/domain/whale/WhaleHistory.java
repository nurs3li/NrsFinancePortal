package com.nurseli.nrsfinanceportal.domain.whale;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "whale_history")
public class WhaleHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "whale_level", nullable = false)
    private WhaleLevel whaleLevel;

    @Column(name = "impact_score")
    private Integer impactScore;

    @Column(name = "reason")
    private String reason;

    @Column(name = "daily_volume", precision = 30, scale = 10)
    private BigDecimal dailyVolume;

    @Column(name = "hourly_transaction_count")
    private Integer hourlyTransactionCount;

    @Column(name = "max_single_transaction", precision = 30, scale = 10)
    private BigDecimal maxSingleTransaction;

    @Column(name = "pattern", length = 64)
    private String pattern;

    @Column(name = "behavior", length = 64)
    private String behavior;

    @Column(name = "risk", length = 64)
    private String risk;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WhaleHistory() {}

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

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

    public static WhaleHistory ofDetailed(
            Long userId,
            WhaleLevel whaleLevel,
            Integer impactScore,
            String reason,
            BigDecimal dailyVolume,
            int hourlyTransactionCount,
            BigDecimal maxSingleTransaction,
            String pattern,
            String behavior,
            String risk,
            Instant triggeredAt
    ) {
        WhaleHistory h = new WhaleHistory();
        h.userId = userId;
        h.whaleLevel = whaleLevel;
        h.impactScore = impactScore;
        h.reason = reason;
        h.dailyVolume = dailyVolume;
        h.hourlyTransactionCount = hourlyTransactionCount;
        h.maxSingleTransaction = maxSingleTransaction;
        h.pattern = pattern;
        h.behavior = behavior;
        h.risk = risk;
        h.triggeredAt = triggeredAt;
        return h;
    }
}