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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "whale_level", nullable = false)
    private WhaleLevel whaleLevel;

    @Column(name = "reason")
    private String reason;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WhaleHistory() {}

    public static WhaleHistory of(
            Long userId,
            WhaleLevel level,
            String reason,
            Instant triggeredAt
    ) {
        WhaleHistory h = new WhaleHistory();
        h.userId = userId;
        h.whaleLevel = level;
        h.reason = reason;
        h.triggeredAt = triggeredAt;
        return h;
    }
}
