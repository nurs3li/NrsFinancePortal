package com.nurseli.marketdata.infrastructure.bist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.ViopHybridProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ViopPriceFallbackClient {
    private final ViopHybridProperties properties;
    private final WebClient webClient = WebClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ViopPriceFallbackClient(ViopHybridProperties properties) {
        this.properties = properties;
    }

    public List<ViopFallbackPriceRow> fetchRows() {
        if (!properties.isEnabled() || !properties.isPriceFallbackEnabled()) {
            return List.of();
        }
        if (properties.getPriceFallbackUrl() == null || properties.getPriceFallbackUrl().isBlank()) {
            return List.of();
        }
        try {
            String body = webClient.get()
                    .uri(properties.getPriceFallbackUrl())
                    .header(HttpHeaders.AUTHORIZATION, bearer(properties.getPriceFallbackApiKey()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofMillis(properties.getTimeoutMs()))
                    .onErrorResume(ex -> {
                        log.warn("[VIOP_PRICE_FALLBACK] fetch failed: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block();
            if (body == null || body.isBlank()) {
                return List.of();
            }
            return parseRows(body);
        } catch (Exception ex) {
            log.warn("[VIOP_PRICE_FALLBACK] parse failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ViopFallbackPriceRow> parseRows(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode array = root.isArray() ? root : root.path("data");
        if (!array.isArray()) {
            return List.of();
        }
        List<ViopFallbackPriceRow> out = new ArrayList<>();
        for (JsonNode node : array) {
            String code = node.path("contractCode").asText(null);
            if (code == null || code.isBlank()) continue;
            BigDecimal price = parseDecimal(node.path("price").asText(null));
            Long latency = node.path("latencyMs").isNumber() ? node.path("latencyMs").asLong() : null;
            String source = node.path("source").asText("PRICE_FALLBACK");
            out.add(new ViopFallbackPriceRow(code, price, source, latency, LocalDateTime.now()));
        }
        return out;
    }

    private String bearer(String apiKey) {
        return (apiKey == null || apiKey.isBlank()) ? "" : "Bearer " + apiKey;
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.replace(",", "."));
        } catch (Exception ignored) {
            return null;
        }
    }

    public record ViopFallbackPriceRow(
            String contractCode,
            BigDecimal price,
            String source,
            Long latencyMs,
            LocalDateTime asOf
    ) {}
}

