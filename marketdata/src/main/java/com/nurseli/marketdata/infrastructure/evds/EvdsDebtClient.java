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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        return fetchLatest(null);
    }

    public List<EvdsDebtRow> fetchLatest(Integer lookbackDaysOverride) {
        if (!evdsProperties.isEnabled() || evdsProperties.getDebt() == null || !evdsProperties.getDebt().isEnabled()) {
            return List.of();
        }
        List<EvdsProperties.Instrument> instruments = evdsProperties.getDebt().getInstruments();
        if (instruments == null || instruments.isEmpty()) {
            return List.of();
        }

        List<EvdsDebtRow> out = new ArrayList<>();
        int pointLimit = Math.max(2, resolveLookbackDays(lookbackDaysOverride));
        for (EvdsProperties.Instrument instrument : instruments) {
            if (instrument == null || instrument.getIsin() == null || instrument.getIsin().isBlank()) {
                continue;
            }
            String isin = instrument.getIsin().trim().toUpperCase();
            List<EvdsSeriesPoint> pricePoints = fetchRecentSeriesPoints(instrument.getDirtyPriceSeries(), pointLimit);
            String couponSeries = instrument.couponRateSeries();
            List<EvdsSeriesPoint> couponPoints = fetchRecentSeriesPoints(couponSeries, pointLimit);
            EvdsSeriesPoint pricePoint = pricePoints.isEmpty() ? null : pricePoints.get(0);
            EvdsSeriesPoint couponPoint = couponPoints.isEmpty() ? null : couponPoints.get(0);
            if (pricePoint == null && couponPoint == null) {
                continue;
            }
            BigDecimal dirtyPrice = pricePoint != null
                    ? normalizeByScale(pricePoint.value(), instrument.getDirtyPriceScale())
                    : null;
            BigDecimal couponRate = couponPoint != null
                    ? normalizeByScale(couponPoint.value(), instrument.couponRateScale())
                    : null;
            LocalDateTime asOf = pricePoint != null
                    ? pricePoint.asOf()
                    : (couponPoint != null ? couponPoint.asOf() : LocalDateTime.now());

            out.add(new EvdsDebtRow(
                    isin,
                    instrument.getName(),
                    instrument.getIssuer(),
                    instrument.getMaturityDate(),
                    dirtyPrice,
                    couponRate,
                    asOf,
                    "EVDS"
            ));
        }
        return out;
    }

    /**
     * EVDS serisini tarih artan sırada döndürür (örn. aylık TÜFE endeks seviyesi).
     * Seri kodu {@code market.evds.series.CPI_TR_INDEX} üzerinden yapılandırılır.
     */
    public List<EvdsSeriesPoint> fetchSeriesAscending(String seriesCode, LocalDate startInclusive, LocalDate endInclusive) {
        if (!evdsProperties.isEnabled() || seriesCode == null || seriesCode.isBlank()) {
            return List.of();
        }
        String apiKey = evdsProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[EVDS_SERIES] skip {} — EVDS_API_KEY is empty", seriesCode);
            return List.of();
        }
        String normalizedSeries = normalizeSeriesCode(seriesCode);
        String json = webClient.get()
                .uri(buildSeriesUri(normalizedSeries, startInclusive, endInclusive))
                .header("key", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(java.time.Duration.ofMillis(evdsProperties.getTimeoutMs()))
                .retryWhen(Retry.max(2))
                .onErrorResume(ex -> {
                    log.warn(
                            "[EVDS_SERIES] fetch failed series={} baseUrl={} range={}..{} msg={}",
                            normalizedSeries,
                            evdsProperties.getBaseUrl(),
                            startInclusive,
                            endInclusive,
                            ex.getMessage()
                    );
                    return Mono.empty();
                })
                .block();
        if (json == null || json.isBlank()) {
            log.warn(
                    "[EVDS_SERIES] empty response series={} baseUrl={} range={}..{}",
                    normalizedSeries,
                    evdsProperties.getBaseUrl(),
                    startInclusive,
                    endInclusive
            );
            return List.of();
        }
        List<EvdsSeriesPoint> parsed = parseAllPoints(json);
        parsed.sort(Comparator.comparing(EvdsSeriesPoint::asOf));
        return dedupeByCalendarDayKeepLast(parsed);
    }

    private int resolveLookbackDays(Integer overrideDays) {
        if (overrideDays != null && overrideDays > 0) {
            return overrideDays;
        }
        return Math.max(1, evdsProperties.getDebt().getLookbackDays());
    }

    private List<EvdsSeriesPoint> fetchRecentSeriesPoints(String seriesCode, int limit) {
        if (seriesCode == null || seriesCode.isBlank()) {
            return List.of();
        }
        String normalizedSeries = normalizeSeriesCode(seriesCode);
        LocalDate end = LocalDate.now();
        int lookbackDays = Math.max(3, limit);
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
            return List.of();
        }
        return parseRecentPoints(json, Math.max(1, limit));
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

    private List<EvdsSeriesPoint> parseRecentPoints(String json, int limit) {
        List<EvdsSeriesPoint> parsed = parseAllPoints(json);
        if (parsed.isEmpty()) {
            return List.of();
        }
        parsed.sort((a, b) -> b.asOf().compareTo(a.asOf()));
        List<EvdsSeriesPoint> uniqueByDate = new ArrayList<>();
        for (EvdsSeriesPoint point : parsed) {
            boolean exists = uniqueByDate.stream().anyMatch(x -> x.asOf().toLocalDate().equals(point.asOf().toLocalDate()));
            if (exists) {
                continue;
            }
            uniqueByDate.add(point);
            if (uniqueByDate.size() >= limit) {
                break;
            }
        }
        return uniqueByDate;
    }

    private List<EvdsSeriesPoint> parseAllPoints(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return List.of();
            }
            List<EvdsSeriesPoint> parsed = new ArrayList<>();
            for (JsonNode item : items) {
                String dateText = item.path("Tarih").asText(null);
                if (dateText == null || dateText.isBlank()) {
                    dateText = item.path("DATE").asText(null);
                }
                if (dateText == null || dateText.isBlank()) {
                    continue;
                }
                LocalDate date = EvdsObservationDateParser.parse(dateText);
                if (date == null) {
                    continue;
                }
                BigDecimal value = firstNumericValue(item);
                if (value == null) {
                    continue;
                }
                parsed.add(new EvdsSeriesPoint(date.atStartOfDay(), value));
            }
            return parsed;
        } catch (Exception ex) {
            log.warn("[EVDS_DEBT] parse error: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<EvdsSeriesPoint> dedupeByCalendarDayKeepLast(List<EvdsSeriesPoint> sortedAsc) {
        Map<LocalDate, EvdsSeriesPoint> map = new LinkedHashMap<>();
        for (EvdsSeriesPoint p : sortedAsc) {
            LocalDate d = p.asOf().toLocalDate();
            map.put(d, p);
        }
        return new ArrayList<>(map.values());
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

    private BigDecimal normalizeByScale(BigDecimal value, BigDecimal scale) {
        if (value == null) {
            return null;
        }
        if (scale == null || scale.compareTo(BigDecimal.ZERO) <= 0 || BigDecimal.ONE.compareTo(scale) == 0) {
            return value;
        }
        return value.divide(scale, 6, java.math.RoundingMode.HALF_UP);
    }

    public record EvdsDebtRow(
            String isin,
            String name,
            String issuer,
            String maturityDate,
            /** EVDS “Değer” — kirli fiyat / piyasa değeri. */
            BigDecimal dirtyPrice,
            /** EVDS “Kupon Faiz Oranı” (_ORAN serisi); YTM değildir. */
            BigDecimal couponRate,
            LocalDateTime asOf,
            String source
    ) {}
}
