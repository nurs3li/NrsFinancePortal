package com.nurseli.whaleanalytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka.topics")
public class WhaleKafkaTopicProperties {

    private String investmentPositionCreated = "finance.investment.position.created";
    private String investmentPositionUpdated = "finance.investment.position.updated";
    private String investmentPositionClosed = "finance.investment.position.closed";
    private String investorBehaviorUpdated = "investor.behavior.updated";
}
