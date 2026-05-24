package com.nurseli.notificationservice.integration.support;

import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.domain.Notification;

import java.time.Instant;

public final class NotificationIntegrationFixtures {

    public static final String TEST_USER_SUB = "integration-notification-user-sub";
    public static final String OTHER_USER_SUB = "integration-notification-other-sub";

    private NotificationIntegrationFixtures() {
    }

    public static Notification unreadNotification(String userSub, String title, String type) {
        Instant now = Instant.now();
        return Notification.builder()
                .userSub(userSub)
                .title(title)
                .body("Integration test body for " + title)
                .type(type)
                .referenceType("PORTFOLIO")
                .referenceId(42L)
                .occurrenceCount(1)
                .createdAt(now)
                .lastOccurredAt(now)
                .build();
    }

    public static NotificationRequestedEvent inAppOnlyEvent(String userSub, String title) {
        return new NotificationRequestedEvent(
                userSub,
                title,
                "Kafka integration test body",
                "REAL_RETURN_NEGATIVE",
                "PORTFOLIO",
                99L);
    }
}
