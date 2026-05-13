package com.nurseli.whaleanalytics.config;

import com.nurseli.whaleanalytics.event.investment.InvestmentPositionClosedEvent;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionCreatedEvent;
import com.nurseli.whaleanalytics.event.investment.InvestmentPositionUpdatedEvent;
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
public class InvestmentPositionKafkaConsumerConfig {

    private static final String TRUSTED_PACKAGES = "com.nurseli.whaleanalytics.event.investment";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, InvestmentPositionCreatedEvent> investmentCreatedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps("whale-investor-created-base"), new StringDeserializer(),
                jsonDeserializer(InvestmentPositionCreatedEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InvestmentPositionCreatedEvent>
    investmentCreatedKafkaListenerContainerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, InvestmentPositionCreatedEvent>();
        f.setConsumerFactory(investmentCreatedConsumerFactory());
        f.setCommonErrorHandler(new DefaultErrorHandler());
        return f;
    }

    @Bean
    public ConsumerFactory<String, InvestmentPositionUpdatedEvent> investmentUpdatedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps("whale-investor-updated-base"), new StringDeserializer(),
                jsonDeserializer(InvestmentPositionUpdatedEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InvestmentPositionUpdatedEvent>
    investmentUpdatedKafkaListenerContainerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, InvestmentPositionUpdatedEvent>();
        f.setConsumerFactory(investmentUpdatedConsumerFactory());
        f.setCommonErrorHandler(new DefaultErrorHandler());
        return f;
    }

    @Bean
    public ConsumerFactory<String, InvestmentPositionClosedEvent> investmentClosedConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProps("whale-investor-closed-base"), new StringDeserializer(),
                jsonDeserializer(InvestmentPositionClosedEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InvestmentPositionClosedEvent>
    investmentClosedKafkaListenerContainerFactory() {
        var f = new ConcurrentKafkaListenerContainerFactory<String, InvestmentPositionClosedEvent>();
        f.setConsumerFactory(investmentClosedConsumerFactory());
        f.setCommonErrorHandler(new DefaultErrorHandler());
        return f;
    }

    private Map<String, Object> consumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    private static <T> JsonDeserializer<T> jsonDeserializer(Class<T> type) {
        JsonDeserializer<T> d = new JsonDeserializer<>(type);
        d.addTrustedPackages(TRUSTED_PACKAGES);
        d.setUseTypeHeaders(false);
        return d;
    }
}
