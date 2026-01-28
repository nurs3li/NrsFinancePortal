package com.nurseli.logconsumer.config;

import com.nurseli.logconsumer.event.TransactionCreatedEvent;
import com.nurseli.logconsumer.event.TransactionReversedEvent;
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

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /* ================= CREATED ================= */

    @Bean
    public ConsumerFactory<String, TransactionCreatedEvent>
    createdConsumerFactory() {

        JsonDeserializer<TransactionCreatedEvent> deserializer =
                new JsonDeserializer<>(TransactionCreatedEvent.class);
        deserializer.addTrustedPackages("*");

        Map<String, Object> props = baseProps("log-consumer-created");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionCreatedEvent>
    createdKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, TransactionCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(createdConsumerFactory());
        return factory;
    }

    /* ================= REVERSED ================= */

    @Bean
    public ConsumerFactory<String, TransactionReversedEvent>
    reversedConsumerFactory() {

        JsonDeserializer<TransactionReversedEvent> deserializer =
                new JsonDeserializer<>(TransactionReversedEvent.class);
        deserializer.addTrustedPackages("*");

        Map<String, Object> props = baseProps("log-consumer-reversed");

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                deserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TransactionReversedEvent>
    reversedKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, TransactionReversedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(reversedConsumerFactory());
        return factory;
    }

    /* ================= COMMON ================= */

    private Map<String, Object> baseProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }
}
