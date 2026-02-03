package com.nurseli.nrsfinanceportal.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class MarketDataWebClientConfig {

    @Bean(name = "marketDataWebClient")
    public WebClient marketDataWebClient(
            @Value("${market-data.base-url}") String baseUrl
    ) {
        System.out.println("### MARKET DATA BASE URL = " + baseUrl);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}