package com.nurseli.marketdata.infrastructure.coingecko;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

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
    private static final int OHLC_429_MAX_ATTEMPTS = 4;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String apiKey;

    public CoinGeckoClient(DataSourcesProperties dataSourcesProperties) {
        DataSourcesProperties.CoinGecko cg = dataSourcesProperties.getCoingecko();
        this.baseUrl = cg.getUrl();
        this.apiKey = cg.getApiKey() == null ? "" : cg.getApiKey().trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1000, cg.getConnectTimeoutMs()));
        factory.setReadTimeout(Math.max(3000, cg.getReadTimeoutMs()));
        this.restTemplate = new RestTemplate(factory);
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (!apiKey.isEmpty()) {
            if (baseUrl.contains("pro-api.coingecko")) {
                headers.set("x-cg-pro-api-key", apiKey);
            } else {
                headers.set("x-cg-demo-api-key", apiKey);
            }
        }
        return headers;
    }

    /**
     * FREE endpoint
     * GET /simple/price
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Double>> fetchPrices(String ids) {
        String url = baseUrl + "/simple/price?ids=" + ids + "&vs_currencies=usd";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("[COINGECKO] Fetching prices for {} (attempt {}/{})", ids, attempt, 3);
                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(baseHeaders()), Map.class);
                Map<?, ?> body = response.getBody();
                return body != null ? (Map<String, Map<String, Double>>) body : Map.of();
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
                sleepQuiet(350L * attempt);
            }
        }
        return Map.of();
    }

    public List<OhlcPoint> fetchDailyOhlc(String coinId, int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        String url = baseUrl + "/coins/" + coinId + "/ohlc?vs_currency=usd&days=" + safeDays;
        for (int attempt = 1; attempt <= OHLC_429_MAX_ATTEMPTS; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                ResponseEntity<List> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(baseHeaders()), List.class);
                List<List<Number>> rows = response.getBody();
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
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && attempt < OHLC_429_MAX_ATTEMPTS) {
                    long waitMs = 65_000L * attempt;
                    log.warn(
                            "[COINGECKO] OHLC 429 rate limit coinId={} days={} — waiting {}s before retry {}/{}",
                            coinId, safeDays, waitMs / 1000, attempt, OHLC_429_MAX_ATTEMPTS);
                    sleepQuiet(waitMs);
                    continue;
                }
                log.warn("[COINGECKO] OHLC fetch failed coinId={} days={} reason={}", coinId, safeDays, ex.getMessage());
                return List.of();
            } catch (RestClientException ex) {
                log.warn("[COINGECKO] OHLC fetch failed coinId={} days={} reason={}", coinId, safeDays, ex.getMessage());
                return List.of();
            }
        }
        return List.of();
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
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
