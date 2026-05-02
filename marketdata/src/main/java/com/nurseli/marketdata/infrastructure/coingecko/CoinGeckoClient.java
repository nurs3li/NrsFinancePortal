package com.nurseli.marketdata.infrastructure.coingecko;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class CoinGeckoClient {
    private static final long WARN_THROTTLE_MS = 5 * 60 * 1000L;
    private static final AtomicLong LAST_WARN_AT = new AtomicLong(0L);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CoinGeckoClient(DataSourcesProperties dataSourcesProperties) {
        this.baseUrl = dataSourcesProperties.getCoingecko().getUrl();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * FREE endpoint
     * GET /simple/price
     */
    public Map<String, Map<String, Double>> fetchPrices(String ids) {
        String url = baseUrl + "/simple/price?ids=" + ids + "&vs_currencies=usd";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("[COINGECKO] Fetching prices for {} (attempt {}/{})", ids, attempt, 3);
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Double>> out = restTemplate.getForObject(url, Map.class);
                return out != null ? out : Map.of();
            } catch (Exception ex) {
                if (attempt == 3) {
                    long now = System.currentTimeMillis();
                    long last = LAST_WARN_AT.get();
                    if (now - last >= WARN_THROTTLE_MS && LAST_WARN_AT.compareAndSet(last, now)) {
                        log.warn("[COINGECKO] Fetch failed after retries: {}", ex.getMessage());
                    } else {
                        log.debug("[COINGECKO] Fetch failed after retries (suppressed warn): {}", ex.getMessage());
                    }
                    return Map.of();
                }
                try {
                    Thread.sleep(350L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Map.of();
                }
            }
        }
        return Map.of();
    }

    public List<OhlcPoint> fetchDailyOhlc(String coinId, int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        String url = baseUrl + "/coins/" + coinId + "/ohlc?vs_currency=usd&days=" + safeDays;
        try {
            @SuppressWarnings("unchecked")
            List<List<Number>> rows = restTemplate.getForObject(url, List.class);
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<OhlcPoint> out = new ArrayList<>();
            for (List<Number> row : rows) {
                if (row == null || row.size() < 5) {
                    continue;
                }
                long epochMs = row.get(0).longValue();
                BigDecimal open = BigDecimal.valueOf(row.get(1).doubleValue());
                BigDecimal high = BigDecimal.valueOf(row.get(2).doubleValue());
                BigDecimal low = BigDecimal.valueOf(row.get(3).doubleValue());
                BigDecimal close = BigDecimal.valueOf(row.get(4).doubleValue());
                if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0) {
                    continue;
                }
                LocalDate day = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate();
                out.add(new OhlcPoint(day, open, high, low, close, null));
            }
            return out;
        } catch (Exception ex) {
            log.warn("[COINGECKO] OHLC fetch failed coinId={} days={} reason={}", coinId, safeDays, ex.getMessage());
            return List.of();
        }
    }

    public record OhlcPoint(
            LocalDate day,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume
    ) {}
}