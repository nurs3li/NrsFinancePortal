package com.nurseli.whaleanalytics.infrastructure.kafka;

import com.nurseli.whaleanalytics.event.WhaleAlertDetectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * @deprecated Eski transaction tabanlı whale uyarıları. Ana akış {@link InvestorBehaviorEventProducer}.
 */
@Deprecated(since = "0.0.1", forRemoval = false)
@Component
@RequiredArgsConstructor
public class WhaleAlertProducer {

    @Value("${app.kafka.topics.whale-alert-triggered:whale.alert.triggered}")
    private String whaleAlertTopic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(WhaleAlertDetectedEvent event) {
        kafkaTemplate.send(
                whaleAlertTopic,
                event.userId().toString(),
                event
        );
    }
}

