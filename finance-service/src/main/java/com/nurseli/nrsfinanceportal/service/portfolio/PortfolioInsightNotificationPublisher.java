package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioInsightNotificationPublisher {

    private final NotificationEventKafkaPublisher kafkaPublisher;

    public List<String> publishAll(List<NotificationRequestedEvent> events) {
        List<String> types = new ArrayList<>();
        if (events == null) {
            return types;
        }
        for (NotificationRequestedEvent event : events) {
            if (event == null || event.type() == null) {
                continue;
            }
            kafkaPublisher.publish(event);
            types.add(event.type());
            log.info("[PORTFOLIO_INSIGHT] Published notification type={} sub={}", event.type(), event.targetKeycloakSub());
        }
        return types;
    }
}
