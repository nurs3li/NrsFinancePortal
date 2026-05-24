package com.nurseli.notificationservice.application.email;

import com.nurseli.notificationservice.domain.email.EmailDeliveryAudit;
import com.nurseli.notificationservice.domain.email.EmailDeliveryStatus;
import com.nurseli.notificationservice.infrastructure.persistence.EmailDeliveryAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * E-posta teslim denemelerinin kalıcı audit kaydını yönetir.
 */
@Service
@RequiredArgsConstructor
public class EmailAuditService {

    private final EmailDeliveryAuditRepository repository;

    /**
     * {@code record} — Tek bir e-posta teslim denemesini audit tablosuna yazar.
     */
    public void record(String sub,
                       String email,
                       String type,
                       EmailDeliveryStatus status,
                       String failureReason,
                       String referenceType,
                       Long referenceId) {
        EmailDeliveryAudit audit = new EmailDeliveryAudit(
                sub,
                email,
                type,
                status,
                failureReason,
                referenceType,
                referenceId
        );
        repository.save(audit);
    }
}