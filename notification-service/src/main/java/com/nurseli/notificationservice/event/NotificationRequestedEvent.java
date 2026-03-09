package com.nurseli.notificationservice.event;

/**
 * notification-events topic'inden okunan payload.
 * finance-service ile aynı alan isimleri (JSON uyumluluğu).
 */
public record NotificationRequestedEvent(
        String targetKeycloakSub,
        String title,
        String body,
        String type,
        String referenceType,
        Long referenceId
) {}