package com.nurseli.marketdata.infrastructure.evds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.EvdsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class EvdsClient {

    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EvdsProperties evdsProperties;

    public EvdsClient(EvdsProperties evdsProperties) {
        this.webClient = WebClient.builder().baseUrl(evdsProperties.getBaseUrl()).build();
        this.objectMapper = new ObjectMapper();
        this.evdsProperties = evdsProperties;
    }

    public List<EvdsFxPoint> getHistoricalFx(String symbol, LocalDate startDate, LocalDate endDate) {
        if (!evdsProperties.isEnabled()) {
            return List.of();
        }
        String series = mapSeries(symbol);
        String json = webClient.get()
                .uri(buildSeriesUri(series, startDate, endDate))
                .header("key", evdsProperties.getApiKey())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofMillis(evdsProperties.getTimeoutMs()))
                .retryWhen(Retry.max(2))
                .onErrorResume(ex -> {
                    log.warn("[EVDS] Historical fetch failed for {}: {}", symbol, ex.getMessage());
                    return Mono.empty();
                })
                .block();

        if (json == null || json.isBlank()) {
            return List.of();
        }
        return parseSeries(symbol, json);
    }

    private String buildSeriesUri(String series, LocalDate startDate, LocalDate endDate) {
        String start = startDate.format(EVDS_DATE);
        String end = endDate.format(EVDS_DATE);
        String baseUrl = evdsProperties.getBaseUrl() == null ? "" : evdsProperties.getBaseUrl().toLowerCase();
        if (baseUrl.contains("igmevdsms-dis")) {
            return "/series=%s&startDate=%s&endDate=%s&type=json".formatted(series, start, end);
        }
        return "/series=%s?startDate=%s&endDate=%s&type=json&key=%s"
                .formatted(series, start, end, evdsProperties.getApiKey());
    }

    private List<EvdsFxPoint> parseSeries(String symbol, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<EvdsFxPoint> out = new ArrayList<>();
            for (JsonNode item : items) {
                String dateText = item.path("Tarih").asText(null);
                if (dateText == null || dateText.isBlank()) {
                    dateText = item.path("DATE").asText(null);
                }
                if (dateText == null || dateText.isBlank()) {
                    continue;
                }
                LocalDate date = LocalDate.parse(dateText, EVDS_DATE);
                BigDecimal price = findPrice(item, symbol);
                if (price == null) {
                    continue;
                }
                out.add(new EvdsFxPoint(normalizeSymbol(symbol), date.atStartOfDay(), price));
            }
            return out;
        } catch (Exception ex) {
            log.warn("[EVDS] Parse error for symbol {}: {}", symbol, ex.getMessage());
            return List.of();
        }
    }

    private BigDecimal findPrice(JsonNode item, String symbol) {
        String needle = symbol.toUpperCase().contains("USD") ? "USD" : "EUR";
        var fieldNames = item.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            if (!key.toUpperCase().contains(needle)) {
                continue;
            }
            String raw = item.path(key).asText();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                return new BigDecimal(raw.replace(",", "."));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String mapSeries(String symbol) {
        return switch (normalizeSymbol(symbol)) {
            case "USDTRY" -> "TP.DK.USD.A.YTL";
            case "EURTRY" -> "TP.DK.EUR.A.YTL";
            default -> throw new IllegalArgumentException("Unsupported EVDS symbol: " + symbol);
        };
    }

    private String normalizeSymbol(String symbol) {
        String s = symbol == null ? "" : symbol.trim().toUpperCase();
        if ("USD".equals(s)) return "USDTRY";
        if ("EUR".equals(s)) return "EURTRY";
        return s;
    }

    public record EvdsFxPoint(String symbol, LocalDateTime timestamp, BigDecimal price) {
    }
}
