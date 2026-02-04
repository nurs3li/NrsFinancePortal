package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarketDataClient {

    private final WebClient marketDataWebClient;

    public Map<String, MarketPriceLatestDto> getLatestDoviz() {
        return marketDataWebClient.get()
                .uri("/api/market/doviz/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<
                        Map<String, MarketPriceLatestDto>>() {})
                .block();
    }

    public Map<String, MarketPriceLatestDto> getLatestMetals() {
        return marketDataWebClient.get()
                .uri("/api/market/metals/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<
                        Map<String, MarketPriceLatestDto>>() {})
                .block();
    }

    public Map<String, MarketPriceLatestDto> getLatestCrypto() {
        return marketDataWebClient.get()
                .uri("/api/market/crypto/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<
                        Map<String, MarketPriceLatestDto>>() {})
                .block();
    }

    // ✅ TEFAS – AYNI DTO
    public Map<String, MarketPriceLatestDto> getLatestFunds() {
        return marketDataWebClient.get()
                .uri("/api/market/funds/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<
                        Map<String, MarketPriceLatestDto>>() {})
                .block();
    }
}