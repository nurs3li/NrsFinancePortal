package com.nurseli.notificationservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPrePersistTest {

  @Test
  void prePersist_setsCreatedAtAndLastOccurredAtWhenMissing() {
    Notification notification = Notification.builder().userSub("sub-1").title("Hi").type("ALERT").build();

    notification.createdAt();

    assertThat(notification.getCreatedAt()).isNotNull();
    assertThat(notification.getLastOccurredAt()).isEqualTo(notification.getCreatedAt());
  }

  @Test
  void prePersist_preservesExplicitTimestamps() {
    Instant explicit = Instant.parse("2026-01-15T10:00:00Z");
    Instant lastOccurred = Instant.parse("2026-01-16T12:00:00Z");
    Notification notification =
        Notification.builder()
            .userSub("sub-1")
            .title("Hi")
            .type("ALERT")
            .createdAt(explicit)
            .lastOccurredAt(lastOccurred)
            .build();

    notification.createdAt();

    assertThat(notification.getCreatedAt()).isEqualTo(explicit);
    assertThat(notification.getLastOccurredAt()).isEqualTo(lastOccurred);
  }
}
