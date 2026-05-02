package com.nurseli.marketdata.infrastructure.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class YahooChartClient {
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YahooChartClient(DataSourcesProperties dataSourcesProperties) {
        String baseUrl = dataSourcesProperties.getYahoo() != null
                ? dataSourcesProperties.getYahoo().getUrl()
                : null;
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://query1.finance.yahoo.com";
        }
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<YahooDailyBar> fetchDailyBars(String symbol) {
        return fetchDailyBars(symbol, "1y");
    }

    public List<YahooDailyBar> fetchDailyBars(String symbol, String range) {
        String yahooSymbol = toYahooSymbol(symbol);
        String safeRange = (range == null || range.isBlank()) ? "1y" : range;
        String body = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v8/finance/chart/{symbol}")
                        .queryParam("interval", "1d")
                        .queryParam("range", safeRange)
                        .build(yahooSymbol))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorReturn("")
                .block();
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return List.of();
            }
            JsonNode first = result.get(0);
            JsonNode timestamps = first.path("timestamp");
            JsonNode quote = first.path("indicators").path("quote");
            if (!quote.isArray() || quote.isEmpty()) {
                return List.of();
            }
            JsonNode firstQuote = quote.get(0);
            JsonNode opens = firstQuote.path("open");
            JsonNode highs = firstQuote.path("high");
            JsonNode lows = firstQuote.path("low");
            JsonNode closes = firstQuote.path("close");
            JsonNode volumes = firstQuote.path("volume");
            if (!timestamps.isArray() || !closes.isArray() || !opens.isArray() || !highs.isArray() || !lows.isArray()) {
                return List.of();
            }
            int size = List.of(timestamps.size(), opens.size(), highs.size(), lows.size(), closes.size()).stream()
                    .min(Integer::compareTo)
                    .orElse(0);
            List<YahooDailyBar> out = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                JsonNode tsNode = timestamps.get(i);
                JsonNode openNode = opens.get(i);
                JsonNode highNode = highs.get(i);
                JsonNode lowNode = lows.get(i);
                JsonNode closeNode = closes.get(i);
                if (tsNode == null || tsNode.isNull()
                        || openNode == null || openNode.isNull()
                        || highNode == null || highNode.isNull()
                        || lowNode == null || lowNode.isNull()
                        || closeNode == null || closeNode.isNull()) {
                    continue;
                }
                long epoch = tsNode.asLong();
                double open = openNode.asDouble(0.0);
                double high = highNode.asDouble(0.0);
                double low = lowNode.asDouble(0.0);
                double close = closeNode.asDouble(0.0);
                if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
                    continue;
                }
                LocalDate day = Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
                BigDecimal volume = null;
                if (volumes.isArray() && i < volumes.size()) {
                    JsonNode volumeNode = volumes.get(i);
                    if (volumeNode != null && !volumeNode.isNull()) {
                        volume = BigDecimal.valueOf(volumeNode.asDouble(0.0));
                    }
                }
                out.add(new YahooDailyBar(
                        day,
                        BigDecimal.valueOf(open),
                        BigDecimal.valueOf(high),
                        BigDecimal.valueOf(low),
                        BigDecimal.valueOf(close),
                        volume
                ));
            }
            return out;
        } catch (Exception ex) {
            log.warn("[YAHOO] Failed to parse chart response symbol={} reason={}", symbol, ex.getMessage());
            return List.of();
        }
    }

    private String toYahooSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalized.isBlank()) {
            return normalized;
        }
        if ("USDTRY".equals(normalized)) return "USDTRY=X";
        if ("EURTRY".equals(normalized)) return "EURTRY=X";
        if ("GBPTRY".equals(normalized)) return "GBPTRY=X";
        return normalized.contains(".") ? normalized : normalized;
    }

    public record YahooDailyBar(
            LocalDate day,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume
    ) {}
}
