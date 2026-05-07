package com.nurseli.marketdata.infrastructure.coingecko;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoinGeckoMetalClient {

    private final DataSourcesProperties dataSourcesProperties;

    // PAX GOLD = 1 token = 1 ONS ALTIN
    private static final String PAX_GOLD_ENDPOINT = "/simple/price";
    private static final String PAX_GOLD_HISTORY_ENDPOINT = "/coins/pax-gold/market_chart";
    private static final String PAX_GOLD_OHLC_ENDPOINT = "/coins/pax-gold/ohlc";

    /**
     * @return TRY / ONS, or null if rate limited (429) or invalid response
     */
    public BigDecimal fetchGoldTryPerOunce() {
        String baseUrl = dataSourcesProperties.getCoingecko().getUrl();
        String url = baseUrl + PAX_GOLD_ENDPOINT + "?ids=pax-gold&vs_currencies=try";

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(3000);
            factory.setReadTimeout(3000);
            RestTemplate restTemplate = new RestTemplate(factory);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !response.containsKey("pax-gold")) {
                log.warn("[COINGECKO] PAXG response invalid or empty");
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> paxGold = (Map<String, Object>) response.get("pax-gold");

            Object tryValue = paxGold.get("try");
            if (tryValue == null) {
                log.warn("[COINGECKO] TRY price missing for PAXG");
                return null;
            }

            BigDecimal ouncePrice = new BigDecimal(tryValue.toString());
            log.info("[COINGECKO] PAX GOLD ounce price TRY = {}", ouncePrice);
            return ouncePrice;
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("[COINGECKO] Rate limit (429) - skipping metal price update. Reduce scheduler frequency or use paid plan.");
            return null;
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("[COINGECKO] API error {} - {}", e.getStatusCode(), e.getMessage());
            return null;
        }
    }

    /**
     * CoinGecko market_chart: günlük tarihsel TRY/ONS fiyatları.
     */
    public List<DailyGoldTryPoint> fetchGoldTryHistoryPerOunce(int days) {
        String baseUrl = dataSourcesProperties.getCoingecko().getUrl();
        String url = baseUrl + PAX_GOLD_HISTORY_ENDPOINT + "?vs_currency=try&days=" + days + "&interval=daily";
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(8000);
            RestTemplate restTemplate = new RestTemplate(factory);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !(response.get("prices") instanceof List<?> prices)) {
                return List.of();
            }

            List<DailyGoldTryPoint> out = new ArrayList<>();
            for (Object rowObj : prices) {
                if (!(rowObj instanceof List<?> row) || row.size() < 2) continue;
                Object tsObj = row.get(0);
                Object pxObj = row.get(1);
                if (tsObj == null || pxObj == null) continue;
                long epochMs = new BigDecimal(tsObj.toString()).longValue();
                LocalDate date = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate();
                BigDecimal ounceTry = new BigDecimal(pxObj.toString());
                if (ounceTry.signum() <= 0) continue;
                out.add(new DailyGoldTryPoint(date, ounceTry));
            }
            return out;
        } catch (Exception e) {
            log.warn("[COINGECKO] Failed to fetch metal history: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * CoinGecko OHLC: günlük TRY/ONS mum verisi (open/high/low/close).
     */
    public List<DailyGoldTryOhlcPoint> fetchGoldTryOhlcHistoryPerOunce(int days) {
        String baseUrl = dataSourcesProperties.getCoingecko().getUrl();
        String url = baseUrl + PAX_GOLD_OHLC_ENDPOINT + "?vs_currency=try&days=" + days;
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(8000);
            RestTemplate restTemplate = new RestTemplate(factory);
            @SuppressWarnings("unchecked")
            List<Object> rows = restTemplate.getForObject(url, List.class);
            if (rows == null || rows.isEmpty()) return List.of();

            List<DailyGoldTryOhlcPoint> out = new ArrayList<>();
            for (Object rowObj : rows) {
                if (!(rowObj instanceof List<?> row) || row.size() < 5) continue;
                long epochMs = new BigDecimal(String.valueOf(row.get(0))).longValue();
                LocalDate date = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate();
                BigDecimal open = new BigDecimal(String.valueOf(row.get(1)));
                BigDecimal high = new BigDecimal(String.valueOf(row.get(2)));
                BigDecimal low = new BigDecimal(String.valueOf(row.get(3)));
                BigDecimal close = new BigDecimal(String.valueOf(row.get(4)));
                if (open.signum() <= 0 || high.signum() <= 0 || low.signum() <= 0 || close.signum() <= 0) continue;
                out.add(new DailyGoldTryOhlcPoint(date, open, high, low, close));
            }
            return out;
        } catch (Exception e) {
            log.warn("[COINGECKO] Failed to fetch metal OHLC history: {}", e.getMessage());
            return List.of();
        }
    }

    public record DailyGoldTryPoint(LocalDate date, BigDecimal ounceTry) {}
    public record DailyGoldTryOhlcPoint(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {}
}