package com.nurseli.marketdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.ViopHybridProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViopVolatilityAlertPublisher {
    private final ViopHybridProperties properties;
    private final ObjectMapper objectMapper;

    public void publishOpenInterestSurge(String contractCode, Long previousOi, Long latestOi, double pctChange) {
        if (!properties.isEnabled() || !properties.isKafkaEnabled()) {
            return;
        }
        if (properties.getKafkaBootstrapServers() == null || properties.getKafkaBootstrapServers().isBlank()) {
            return;
        }
        try {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafkaBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
                String payload = objectMapper.writeValueAsString(Map.of(
                        "eventType", "HighVolatilityAlert",
                        "contractCode", contractCode,
                        "previousOpenInterest", previousOi,
                        "latestOpenInterest", latestOi,
                        "changePct", pctChange,
                        "occurredAt", LocalDateTime.now().toString()
                ));
                producer.send(new ProducerRecord<>(properties.getKafkaTopic(), contractCode, payload));
                producer.flush();
            }
        } catch (Exception ex) {
            log.warn("[VIOP_KAFKA] publish failed for {}: {}", contractCode, ex.getMessage());
        }
    }
}

