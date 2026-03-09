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
        String referenceType,
        Long referenceId
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
                n.getReferenceType(),
                n.getReferenceId()
        );
    }
}