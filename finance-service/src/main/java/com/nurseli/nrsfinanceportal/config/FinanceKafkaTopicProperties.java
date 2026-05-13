package com.nurseli.nrsfinanceportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.kafka.topics")
public class FinanceKafkaTopicProperties {

    private String investmentPositionCreated = "finance.investment.position.created";
    private String investmentPositionUpdated = "finance.investment.position.updated";
    private String investmentPositionClosed = "finance.investment.position.closed";
}
