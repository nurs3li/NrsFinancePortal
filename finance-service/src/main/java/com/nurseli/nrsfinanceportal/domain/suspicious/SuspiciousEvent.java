package com.nurseli.nrsfinanceportal.domain.suspicious;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "suspicious_events")
public class SuspiciousEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "reason", nullable = false, length = 50)
    private String reason; // HIGH_FREQUENCY | HIGH_AMOUNT

    @Column(name = "amount", precision = 30, scale = 10)
    private BigDecimal amount;

    @Column(name = "count_in_window")
    private Long countInWindow;

    @Column(name = "threshold_amount", precision = 30, scale = 10)
    private BigDecimal thresholdAmount;

    @Column(name = "threshold_count")
    private Integer thresholdCount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SuspiciousEvent() {
        // JPA
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public static SuspiciousEvent of(
            Long userId,
            Long transactionId,
            String reason,
            BigDecimal amount,
            Long countInWindow,
            BigDecimal thresholdAmount,
            Integer thresholdCount,
            Instant occurredAt
    ) {
        SuspiciousEvent e = new SuspiciousEvent();
        e.userId = userId;
        e.transactionId = transactionId;
        e.reason = reason;
        e.amount = amount;
        e.countInWindow = countInWindow;
        e.thresholdAmount = thresholdAmount;
        e.thresholdCount = thresholdCount;
        e.occurredAt = occurredAt;
        return e;
    }
}