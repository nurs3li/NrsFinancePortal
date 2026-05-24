package com.nurseli.nrsfinanceportal.infrastructure.kafka.event;

/**
 * notification-events topic'ine gönderilen payload.
 * notification-service ile aynı alan isimleri (JSON uyumluluğu).
 */
public record NotificationRequestedEvent(
        String targetKeycloakSub,
        String title,
        String body,
        String type,
        String referenceType,
        Long referenceId
) {}
