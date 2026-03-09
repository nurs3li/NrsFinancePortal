package com.nurseli.notificationservice.consumer;

import com.nurseli.notificationservice.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private static final String TOPIC = "notification-events";

    private final NotificationService notificationService;

    @KafkaListener(topics = TOPIC, groupId = "notification-service-consumer")
    public void consume(NotificationRequestedEvent event) {
        if (event == null || event.targetKeycloakSub() == null || event.targetKeycloakSub().isBlank()) {
            log.warn("[{}] Skipping event with empty targetKeycloakSub", TOPIC);
            return;
        }
        try {
            notificationService.create(
                    event.targetKeycloakSub(),
                    event.title() != null ? event.title() : "",
                    event.body(),
                    event.type() != null ? event.type() : "NOTIFICATION",
                    event.referenceType(),
                    event.referenceId()
            );
            log.info("[{}] Created notification for sub={} type={}", TOPIC, event.targetKeycloakSub(), event.type());
        } catch (Exception e) {
            log.error("[{}] Failed to create notification for sub={} type={}", TOPIC, event.targetKeycloakSub(), event.type(), e);
        }
    }
}