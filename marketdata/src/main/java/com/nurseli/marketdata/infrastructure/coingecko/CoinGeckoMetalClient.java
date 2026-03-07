package com.nurseli.marketdata.infrastructure.coingecko;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoinGeckoMetalClient {

    private final DataSourcesProperties dataSourcesProperties;

    // PAX GOLD = 1 token = 1 ONS ALTIN
    private static final String PAX_GOLD_ENDPOINT = "/simple/price";

    /**
     * @return TRY / ONS, or null if rate limited (429) or invalid response
     */
    public BigDecimal fetchGoldTryPerOunce() {
        String baseUrl = dataSourcesProperties.getCoingecko().getUrl();
        String url = baseUrl + PAX_GOLD_ENDPOINT + "?ids=pax-gold&vs_currencies=try";

        try {
            RestTemplate restTemplate = new RestTemplate();
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
}