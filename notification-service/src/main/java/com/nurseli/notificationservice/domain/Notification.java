package com.nurseli.notificationservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notifications", schema = "nrs_notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_sub", nullable = false, length = 255)
    private String userSub;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Builder.Default
    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount = 1;

    @Column(name = "last_occurred_at")
    private Instant lastOccurredAt;

    @PrePersist
    void createdAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (lastOccurredAt == null) {
            lastOccurredAt = createdAt;
        }
    }
}