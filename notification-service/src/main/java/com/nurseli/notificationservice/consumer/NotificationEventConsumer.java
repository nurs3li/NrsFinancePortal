package com.nurseli.notificationservice.consumer;

import com.nurseli.notificationservice.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.service.NotificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private static final String TOPIC = "notification-events";

    private final NotificationOrchestrator notificationOrchestrator;

    @KafkaListener(topics = TOPIC, groupId = "notification-service-consumer")
    public void consume(NotificationRequestedEvent event) {
        if (event == null || event.targetKeycloakSub() == null || event.targetKeycloakSub().isBlank()) {
            log.warn("[{}] Skipping event with empty targetKeycloakSub", TOPIC);
            return;
        }
        try {
            var notification = notificationOrchestrator.handle(event);
            log.info("[{}] Created notification id={} for sub={} type={}",
                    TOPIC,
                    notification.getId(),
                    event.targetKeycloakSub(),
                    event.type());
        } catch (Exception e) {
            log.error("[{}] Failed to process notification for sub={} type={}",
                    TOPIC, event.targetKeycloakSub(), event.type(), e);
        }
    }
}