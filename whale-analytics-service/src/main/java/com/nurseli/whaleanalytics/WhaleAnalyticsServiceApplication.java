package com.nurseli.whaleanalytics;

import com.nurseli.whaleanalytics.config.InvestorBehaviorAnalysisProperties;
import com.nurseli.whaleanalytics.config.WhaleFeatureProperties;
import com.nurseli.whaleanalytics.config.WhaleKafkaTopicProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@SpringBootApplication
@EnableConfigurationProperties({
        WhaleKafkaTopicProperties.class,
        InvestorBehaviorAnalysisProperties.class,
        WhaleFeatureProperties.class
})
public class WhaleAnalyticsServiceApplication {

	@PostConstruct
	public void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(WhaleAnalyticsServiceApplication.class, args);
	}
}
