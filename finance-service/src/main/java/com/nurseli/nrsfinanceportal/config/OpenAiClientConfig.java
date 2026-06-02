package com.nurseli.nrsfinanceportal.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * finance-service OpenAI istemci konfigÃ¼rasyonu â€” OpenAI API iÃ§in WebClient bean'i tanÄ±mlar.
 */
@Configuration

public class OpenAiClientConfig {

    /**
     * {@code openAiWebClient} â€” OpenAiProperties'e gÃ¶re timeout ve base URL ayarlÄ± WebClient oluÅŸturur.
     */
    @Bean(name = "openAiWebClient")
    public WebClient openAiWebClient(OpenAiProperties properties) {
        int timeoutMs = Math.max(5, properties.getTimeoutSeconds()) * 1000;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofMillis(timeoutMs));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl("https://api.openai.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        if (properties.isConfigured()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey().trim());
        }
        return builder.build();
    }
}
