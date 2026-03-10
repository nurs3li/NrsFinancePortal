package com.nurseli.notificationservice.dto;

import com.nurseli.notificationservice.domain.Notification;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String title,
        String body,
        String type,
        Instant readAt,
        Instant createdAt,
        Instant lastOccurredAt,
        String referenceType,
        Long referenceId,
        int occurrenceCount
) {
    public static NotificationDto from(Notification n) {
        if (n == null) return null;
        return new NotificationDto(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getType(),
                n.getReadAt(),
                n.getCreatedAt(),
                n.getLastOccurredAt(),
                n.getReferenceType(),
                n.getReferenceId(),
                n.getOccurrenceCount()
        );
    }
}