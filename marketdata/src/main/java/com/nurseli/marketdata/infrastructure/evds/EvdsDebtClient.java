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
public class EvdsDebtClient {

    private static final DateTimeFormatter EVDS_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EvdsProperties evdsProperties;

    public EvdsDebtClient(EvdsProperties evdsProperties) {
        this.webClient = WebClient.builder().baseUrl(evdsProperties.getBaseUrl()).build();
        this.objectMapper = new ObjectMapper();
        this.evdsProperties = evdsProperties;
    }

    public List<EvdsDebtRow> fetchLatest() {
        if (!evdsProperties.isEnabled() || evdsProperties.getDebt() == null || !evdsProperties.getDebt().isEnabled()) {
            return List.of();
        }
        List<EvdsProperties.Instrument> instruments = evdsProperties.getDebt().getInstruments();
        if (instruments == null || instruments.isEmpty()) {
            return List.of();
        }

        List<EvdsDebtRow> out = new ArrayList<>();
        for (EvdsProperties.Instrument instrument : instruments) {
            if (instrument == null || instrument.getIsin() == null || instrument.getIsin().isBlank()) {
                continue;
            }
            EvdsPoint pricePoint = fetchLatestSeriesPoint(instrument.getDirtyPriceSeries());
            EvdsPoint yieldPoint = fetchLatestSeriesPoint(instrument.getYieldSeries());
            if (pricePoint == null && yieldPoint == null) {
                continue;
            }
            BigDecimal dirtyPrice = pricePoint != null ? pricePoint.value() : null;
            BigDecimal yieldPct = yieldPoint != null ? yieldPoint.value() : null;
            LocalDateTime asOf = pricePoint != null
                    ? pricePoint.asOf()
                    : (yieldPoint != null ? yieldPoint.asOf() : LocalDateTime.now());

            out.add(new EvdsDebtRow(
                    instrument.getIsin(),
                    instrument.getName(),
                    instrument.getIssuer(),
                    instrument.getMaturityDate(),
                    dirtyPrice,
                    yieldPct,
                    asOf,
                    "EVDS"
            ));
        }
        return out;
    }

    private EvdsPoint fetchLatestSeriesPoint(String seriesCode) {
        if (seriesCode == null || seriesCode.isBlank()) {
            return null;
        }
        String normalizedSeries = normalizeSeriesCode(seriesCode);
        LocalDate end = LocalDate.now();
        int lookbackDays = Math.max(3, evdsProperties.getDebt().getLookbackDays());
        LocalDate start = end.minusDays(lookbackDays);
        String json = webClient.get()
                .uri(buildSeriesUri(normalizedSeries, start, end))
                .header("key", evdsProperties.getApiKey())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofMillis(evdsProperties.getTimeoutMs()))
                .retryWhen(Retry.max(2))
                .onErrorResume(ex -> {
                    log.warn("[EVDS_DEBT] fetch failed for series {}: {}", normalizedSeries, ex.getMessage());
                    return Mono.empty();
                })
                .block();
        if (json == null || json.isBlank()) {
            return null;
        }
        return parseLatestPoint(json);
    }

    private String normalizeSeriesCode(String seriesCode) {
        return seriesCode.trim().replace('_', '.');
    }

    private String buildSeriesUri(String seriesCode, LocalDate start, LocalDate end) {
        String startDate = start.format(EVDS_DATE);
        String endDate = end.format(EVDS_DATE);
        String baseUrl = evdsProperties.getBaseUrl() == null ? "" : evdsProperties.getBaseUrl().toLowerCase();
        if (baseUrl.contains("igmevdsms-dis")) {
            return "/series=%s&startDate=%s&endDate=%s&type=json".formatted(seriesCode, startDate, endDate);
        }
        return "/series=%s?startDate=%s&endDate=%s&type=json&key=%s"
                .formatted(seriesCode, startDate, endDate, evdsProperties.getApiKey());
    }

    private EvdsPoint parseLatestPoint(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return null;
            }
            EvdsPoint latest = null;
            for (JsonNode item : items) {
                String dateText = item.path("Tarih").asText(null);
                if (dateText == null || dateText.isBlank()) {
                    dateText = item.path("DATE").asText(null);
                }
                if (dateText == null || dateText.isBlank()) {
                    continue;
                }
                LocalDate date = LocalDate.parse(dateText, EVDS_DATE);
                BigDecimal value = firstNumericValue(item);
                if (value == null) {
                    continue;
                }
                EvdsPoint point = new EvdsPoint(date.atStartOfDay(), value);
                if (latest == null || point.asOf().isAfter(latest.asOf())) {
                    latest = point;
                }
            }
            return latest;
        } catch (Exception ex) {
            log.warn("[EVDS_DEBT] parse error: {}", ex.getMessage());
            return null;
        }
    }

    private BigDecimal firstNumericValue(JsonNode item) {
        var fieldNames = item.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            String upper = key.toUpperCase();
            if ("TARIH".equals(upper) || "DATE".equals(upper)) {
                continue;
            }
            String raw = item.path(key).asText();
            BigDecimal parsed = parseDecimal(raw);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty() || "-".equals(text)) return null;
        try {
            if (text.contains(",") && text.contains(".")) {
                return new BigDecimal(text.replace(".", "").replace(",", "."));
            }
            if (text.contains(",")) {
                return new BigDecimal(text.replace(",", "."));
            }
            return new BigDecimal(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    public record EvdsDebtRow(
            String isin,
            String name,
            String issuer,
            String maturityDate,
            BigDecimal dirtyPrice,
            BigDecimal yieldPct,
            LocalDateTime asOf,
            String source
    ) {}

    private record EvdsPoint(LocalDateTime asOf, BigDecimal value) {}
}
