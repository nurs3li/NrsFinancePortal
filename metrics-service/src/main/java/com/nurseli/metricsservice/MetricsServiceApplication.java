package com.nurseli.metricsservice;

import com.nurseli.metricsservice.config.MetricsKafkaTopicProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties(MetricsKafkaTopicProperties.class)
public class MetricsServiceApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(MetricsServiceApplication.class, args);
    }

}
