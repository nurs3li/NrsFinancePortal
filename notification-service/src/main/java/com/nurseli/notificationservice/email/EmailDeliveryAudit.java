package com.nurseli.notificationservice.email;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "email_delivery_audit", schema = "public")
public class EmailDeliveryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_sub")
    private String targetSub;

    @Column(name = "target_email")
    private String targetEmail;

    @Column(name = "notification_type")
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EmailDeliveryStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected EmailDeliveryAudit() {
    }

    public EmailDeliveryAudit(String targetSub,
                              String targetEmail,
                              String notificationType,
                              EmailDeliveryStatus status,
                              String failureReason,
                              String referenceType,
                              Long referenceId) {
        this.targetSub = targetSub;
        this.targetEmail = targetEmail;
        this.notificationType = notificationType;
        this.status = status;
        this.failureReason = failureReason;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }
}