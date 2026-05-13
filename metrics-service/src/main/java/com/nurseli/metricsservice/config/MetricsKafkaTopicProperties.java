package com.nurseli.metricsservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka.topics")
public class MetricsKafkaTopicProperties {
    private String investorBehaviorUpdated = "investor.behavior.updated";
}
