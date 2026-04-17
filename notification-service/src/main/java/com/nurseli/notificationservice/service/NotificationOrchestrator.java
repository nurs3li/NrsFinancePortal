package com.nurseli.notificationservice.service;

import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.email.EmailNotificationService;
import com.nurseli.notificationservice.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;

    @Transactional
    public Notification handle(NotificationRequestedEvent event) {
        // In-app notification her zaman önce yazılır
        Notification notification = notificationService.create(
                event.targetKeycloakSub(),
                event.title() != null ? event.title() : "",
                event.body(),
                event.type() != null ? event.type() : "NOTIFICATION",
                event.referenceType(),
                event.referenceId()
        );

        //  E-posta kanalı
        emailNotificationService.sendIfEligible(event);

        return notification;
    }
}