package com.nurseli.marketdata.infrastructure.debt;

import com.nurseli.marketdata.config.ExternalProvidersProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class DebtMarketClient {

    private final ExternalProvidersProperties properties;

    public DebtMarketClient(ExternalProvidersProperties properties) {
        this.properties = properties;
    }

    public List<DebtExternalRow> fetchLatest() {
        if (!properties.getDebt().isEnabled()) {
            return List.of();
        }
        if (properties.getDebt().getBaseUrl() == null || properties.getDebt().getBaseUrl().isBlank()) {
            return List.of();
        }
        WebClient client = WebClient.builder()
                .baseUrl(properties.getDebt().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, bearer(properties.getDebt().getApiKey()))
                .build();
        DebtExternalRow[] rows = client.get()
                .uri(properties.getDebt().getLatestPath())
                .retrieve()
                .bodyToMono(DebtExternalRow[].class)
                .timeout(java.time.Duration.ofMillis(properties.getDebt().getTimeoutMs()))
                .onErrorResume(ex -> Mono.empty())
                .block();
        return rows == null ? List.of() : List.of(rows);
    }

    private String bearer(String apiKey) {
        return (apiKey == null || apiKey.isBlank()) ? "" : "Bearer " + apiKey;
    }

    public record DebtExternalRow(
            String isin,
            String name,
            String issuer,
            String maturityDate,
            BigDecimal dirtyPrice,
            BigDecimal yieldPct,
            LocalDateTime asOf,
            String source
    ) {}
}
