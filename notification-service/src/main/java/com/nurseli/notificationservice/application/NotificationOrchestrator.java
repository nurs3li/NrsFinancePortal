package com.nurseli.notificationservice.application;

import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.application.email.EmailNotificationService;
import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka event tabanlı bildirim akışını koordine eder; in-app kayıt ve e-posta kanalını sırayla işler.
 */
@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;

    /**
     * {@code handle} — Event'i işler: önce in-app bildirimi yazar, ardından uygunsa e-posta kanalını tetikler.
     */
    @Transactional
    public Notification handle(NotificationRequestedEvent event) {
        Notification notification = notificationService.create(
                event.targetKeycloakSub(),
                event.title() != null ? event.title() : "",
                event.body(),
                event.type() != null ? event.type() : "NOTIFICATION",
                event.referenceType(),
                event.referenceId()
        );

        emailNotificationService.sendIfEligible(event);

        return notification;
    }
}
