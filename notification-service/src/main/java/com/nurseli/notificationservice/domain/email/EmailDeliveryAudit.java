package com.nurseli.notificationservice.domain.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Gmail üzerinden yapılan e-posta gönderim denemelerinin denetim kaydını tutar;
 * başarı, politika atlama ve sağlayıcı hatası gibi sonuçlar {@code email_delivery_audit} tablosuna yazılır.
 */
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

    /** JPA için korumalı varsayılan kurucu. */
    protected EmailDeliveryAudit() {
    }

    /**
     * {@code EmailDeliveryAudit} — Yeni bir e-posta gönderim denetim kaydı oluşturur;
     * {@code createdAt} alanı otomatik olarak şu anki zamana ayarlanır.
     */
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