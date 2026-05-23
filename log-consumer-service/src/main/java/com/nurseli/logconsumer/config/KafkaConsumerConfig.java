package com.nurseli.logconsumer.config;

import com.nurseli.logconsumer.application.event.TransactionCreatedEvent;
import com.nurseli.logconsumer.application.event.TransactionReversedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka {@link ConsumerFactory} ve {@link ConcurrentKafkaListenerContainerFactory} bean tanımları;
 * transaction event'leri ve application log JSON payload'ları için ayrı deserializer yapılandırır.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * {@code createdConsumerFactory} — {@link TransactionCreatedEvent} için JSON deserializer'lı consumer factory.
     */
    @Bean
    public ConsumerFactory<String, TransactionCreatedEvent> createdConsumerFactory() {

        JsonDeserializer<TransactionCreatedEvent> deserializer = new JsonDeserializer<>(TransactionCreatedEvent.class);
        deserializer.addTrustedPackages("*");

        Map<String, Object> props = baseProps("log-consumer-created");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * {@code createdKafkaListenerContainerFactory} — created topic listener'ı için container factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionCreatedEvent> createdKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, TransactionCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(createdConsumerFactory());
        return factory;
    }

    /**
     * {@code reversedConsumerFactory} — {@link TransactionReversedEvent} için JSON deserializer'lı consumer factory.
     */
    @Bean
    public ConsumerFactory<String, TransactionReversedEvent> reversedConsumerFactory() {

        JsonDeserializer<TransactionReversedEvent> deserializer = new JsonDeserializer<>(TransactionReversedEvent.class);
        deserializer.addTrustedPackages("*");

        Map<String, Object> props = baseProps("log-consumer-reversed");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    /**
     * {@code reversedKafkaListenerContainerFactory} — reversed topic listener'ı için container factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionReversedEvent> reversedKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, TransactionReversedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(reversedConsumerFactory());
        return factory;
    }

    /**
     * {@code applicationLogsConsumerFactory} — Ham JSON string payload için String deserializer'lı consumer factory.
     */
    @Bean
    public ConsumerFactory<String, String> applicationLogsConsumerFactory() {
        Map<String, Object> props = baseProps("log-consumer-application-logs");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer());
    }

    /**
     * {@code applicationLogsKafkaListenerContainerFactory} — application-logs topic listener'ı için container factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> applicationLogsKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(applicationLogsConsumerFactory());
        return factory;
    }

    /**
     * {@code baseProps} — Ortak Kafka consumer özelliklerini (bootstrap, groupId, offset, auto-commit) üretir.
     */
    private Map<String, Object> baseProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }
}
