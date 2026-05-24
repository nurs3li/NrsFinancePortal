package com.nurseli.notificationservice.api.dto;

import com.nurseli.notificationservice.domain.Notification;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDtoTest {

    @Test
    void from_mapsAllFields() {
        Notification n =
                Notification.builder()
                        .id(1L)
                        .userSub("sub")
                        .title("T")
                        .body("B")
                        .type("ALERT")
                        .readAt(Instant.parse("2025-06-01T10:00:00Z"))
                        .createdAt(Instant.parse("2025-06-01T09:00:00Z"))
                        .lastOccurredAt(Instant.parse("2025-06-01T11:00:00Z"))
                        .referenceType("P")
                        .referenceId(99L)
                        .occurrenceCount(3)
                        .build();

        NotificationDto dto = NotificationDto.from(n);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.title()).isEqualTo("T");
        assertThat(dto.occurrenceCount()).isEqualTo(3);
        assertThat(dto.referenceId()).isEqualTo(99L);
    }

    @Test
    void from_null_returnsNull() {
        assertThat(NotificationDto.from(null)).isNull();
    }
}
