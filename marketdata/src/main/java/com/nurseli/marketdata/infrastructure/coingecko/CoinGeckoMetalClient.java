package com.nurseli.marketdata.infrastructure.coingecko;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
public class CoinGeckoMetalClient {

    // PAX GOLD = 1 token = 1 ONS ALTIN
    private static final String URL =
            "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids=pax-gold&vs_currencies=try";

    /**
     * @return TRY / ONS
     */
    public BigDecimal fetchGoldTryPerOunce() {

        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> response =
                restTemplate.getForObject(URL, Map.class);

        if (response == null || !response.containsKey("pax-gold")) {
            throw new IllegalStateException("CoinGecko PAXG response invalid");
        }

        Map<String, Object> paxGold =
                (Map<String, Object>) response.get("pax-gold");

        Object tryValue = paxGold.get("try");
        if (tryValue == null) {
            throw new IllegalStateException("TRY price missing for PAXG");
        }

        BigDecimal ouncePrice = new BigDecimal(tryValue.toString());

        log.info("[COINGECKO] PAX GOLD ounce price TRY = {}", ouncePrice);
        return ouncePrice;
    }
}