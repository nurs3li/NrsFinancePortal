package com.nurseli.notificationservice.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailAuditService {

    private final EmailDeliveryAuditRepository repository;

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