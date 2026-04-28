package com.nurseli.marketdata.infrastructure.bist;

import com.nurseli.marketdata.config.ExternalProvidersProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class BistViopClient {

    private final ExternalProvidersProperties properties;

    public BistViopClient(ExternalProvidersProperties properties) {
        this.properties = properties;
    }

    public List<ViopExternalRow> fetchLatest() {
        if (!properties.getViop().isEnabled()) {
            return List.of();
        }
        if (properties.getViop().getBaseUrl() == null || properties.getViop().getBaseUrl().isBlank()) {
            return List.of();
        }
        WebClient client = WebClient.builder()
                .baseUrl(properties.getViop().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, bearer(properties.getViop().getApiKey()))
                .build();
        ViopExternalRow[] rows = client.get()
                .uri(properties.getViop().getLatestPath())
                .retrieve()
                .bodyToMono(ViopExternalRow[].class)
                .timeout(java.time.Duration.ofMillis(properties.getViop().getTimeoutMs()))
                .onErrorResume(ex -> Mono.empty())
                .block();
        return rows == null ? List.of() : List.of(rows);
    }

    private String bearer(String apiKey) {
        return (apiKey == null || apiKey.isBlank()) ? "" : "Bearer " + apiKey;
    }

    public record ViopExternalRow(
            String contractCode,
            String underlying,
            String expiry,
            String type,
            BigDecimal price,
            BigDecimal spot,
            Long openInterest,
            LocalDateTime asOf,
            String source
    ) {}
}
