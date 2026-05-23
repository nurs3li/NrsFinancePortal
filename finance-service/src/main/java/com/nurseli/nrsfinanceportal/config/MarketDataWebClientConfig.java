package com.nurseli.nrsfinanceportal.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Market data servisi WebClient bean.
 */
@Configuration
public class MarketDataWebClientConfig {

    @Bean(name = "marketDataWebClient")
    /**
 * Market data servisi HTTP client bean.
 */
    public WebClient marketDataWebClient(
            @Value("${market-data.base-url}") String baseUrl,
            @Value("${market-data.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${market-data.response-timeout-ms:20000}") int responseTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}