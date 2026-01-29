package com.nurseli.whaleanalytics.infrastructure.kafka;

import com.nurseli.whaleanalytics.event.WhaleAlertDetectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhaleAlertProducer {

    private static final String TOPIC = "whale.alert.triggered";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(WhaleAlertDetectedEvent event) {
        kafkaTemplate.send(
                TOPIC,
                event.userId().toString(), // 🔥 TEK DÜZELTME
                event
        );
    }
}

