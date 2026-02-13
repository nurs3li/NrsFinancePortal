package com.nurseli.metricsservice.config;

import com.nurseli.metricsservice.event.SuspiciousActivityDetectedEvent;
import com.nurseli.metricsservice.event.TradeCreatedEvent;
import com.nurseli.metricsservice.event.WhaleAlertDetectedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private static final String TRUSTED_PACKAGES =
            "com.nurseli.metricsservice.event,com.nurseli.nrsfinanceportal.integration.kafka.event,com.nurseli.whaleanalytics.event";

    /* ================= TRADE ================= */

    @Bean
    public ConsumerFactory<String, TradeCreatedEvent> tradeConsumerFactory() {
        Map<String, Object> props = eventConsumerProps("metrics-trade-consumer", TradeCreatedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeCreatedEvent>
    tradeKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TradeCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(tradeConsumerFactory());
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        return factory;
    }

    /* ================= WHALE ================= */

    @Bean
    public ConsumerFactory<String, WhaleAlertDetectedEvent> whaleConsumerFactory() {
        Map<String, Object> props = eventConsumerProps("metrics-whale-consumer", WhaleAlertDetectedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, WhaleAlertDetectedEvent>
    whaleKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, WhaleAlertDetectedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(whaleConsumerFactory());
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        return factory;
    }

    /* ================= SUSPICIOUS ================= */

    @Bean
    public ConsumerFactory<String, SuspiciousActivityDetectedEvent> suspiciousConsumerFactory() {
        Map<String, Object> props = eventConsumerProps("metrics-suspicious-consumer", SuspiciousActivityDetectedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SuspiciousActivityDetectedEvent>
    suspiciousKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SuspiciousActivityDetectedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(suspiciousConsumerFactory());
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        return factory;
    }

    /* ================= COMMON ================= */

    private Map<String, Object> baseProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    private Map<String, Object> eventConsumerProps(String groupId, Class<?> valueType) {
        Map<String, Object> props = baseProps(groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, TRUSTED_PACKAGES);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueType);
        return props;
    }
}