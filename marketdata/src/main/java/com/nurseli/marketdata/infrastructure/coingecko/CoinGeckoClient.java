package com.nurseli.marketdata.infrastructure.coingecko;

import com.nurseli.marketdata.config.DataSourcesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@Slf4j
public class CoinGeckoClient {

    private final WebClient webClient;

    public CoinGeckoClient(DataSourcesProperties dataSourcesProperties) {
        String baseUrl = dataSourcesProperties.getCoingecko().getUrl();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();
    }

    /**
     * FREE endpoint
     * GET /simple/price
     */
    public Map<String, Map<String, Double>> fetchPrices(String ids) {
        try {
            log.info("[COINGECKO] Fetching prices for {}", ids);

            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/simple/price")
                            .queryParam("ids", ids)
                            .queryParam("vs_currencies", "usd")
                            .build()
                    )
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Map<String, Double>>>() {})
                    .block();

        } catch (Exception ex) {
            log.warn("[COINGECKO] Fetch failed: {}", ex.getMessage());
            return Map.of();
        }
    }
}