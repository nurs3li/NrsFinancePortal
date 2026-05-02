package com.nurseli.marketdata.infrastructure.stooq;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
public class StooqCsvClient {
    private final WebClient webClient;
    private final String apiKey;

    public StooqCsvClient(DataSourcesProperties dataSourcesProperties) {
        DataSourcesProperties.Stooq stooq = dataSourcesProperties.getStooq();
        String baseUrl = stooq != null ? stooq.getUrl() : null;
        this.apiKey = stooq != null ? stooq.getApiKey() : null;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://stooq.com";
        }
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.TEXT_PLAIN_VALUE)
                .build();
    }

    public List<StooqDailyRow> fetchDailyRows(String symbol, LocalDate from, LocalDate to) {
        String stooqSymbol = toStooqSymbol(symbol);
        String csv = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/q/d/l/")
                        .queryParam("s", stooqSymbol)
                        .queryParam("i", "d")
                        .queryParamIfPresent("apikey", java.util.Optional.ofNullable(apiKey).filter(k -> !k.isBlank()))
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .onErrorReturn("")
                .block();
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        if (csv.toLowerCase(Locale.ROOT).contains("get your apikey")) {
            log.warn("[STOOQ] API key missing/invalid for symbol={}, cannot fetch fallback history", symbol);
            return List.of();
        }
        String[] lines = csv.split("\\r?\\n");
        if (lines.length < 2) {
            return List.of();
        }
        List<StooqDailyRow> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 6) {
                continue;
            }
            try {
                LocalDate day = LocalDate.parse(cols[0].trim());
                if (day.isBefore(from) || day.isAfter(to)) {
                    continue;
                }
                BigDecimal open = parseDecimal(cols[1]);
                BigDecimal high = parseDecimal(cols[2]);
                BigDecimal low = parseDecimal(cols[3]);
                BigDecimal close = parseDecimal(cols[4]);
                if (open == null || high == null || low == null || close == null || close.signum() <= 0) {
                    continue;
                }
                out.add(new StooqDailyRow(day, open, high, low, close, cols[5].trim()));
            } catch (Exception ignored) {
                // keep parsing next rows
            }
        }
        return out;
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isBlank() || "N/D".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String toStooqSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return normalized;
        }
        return normalized.contains(".") ? normalized : normalized + ".us";
    }

    public record StooqDailyRow(
            LocalDate day,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            String volume
    ) {}
}
