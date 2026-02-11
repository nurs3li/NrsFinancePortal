package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.FxPriceDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarketDataClient {

    private final WebClient marketDataWebClient;

    public Map<String, FxPriceDto> getLatestDoviz() {
        return marketDataWebClient.get()
                .uri("/api/market/doviz/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<
                        Map<String, FxPriceDto>>() {})
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

    // 🔥 TEKİL FİYAT (DASHBOARD / PORTFOLIO İÇİN)
    public BigDecimal getPriceTry(AssetType type, String symbol) {

        return switch (type) {

            case FX -> {
                FxPriceDto fx = getLatestDoviz().get(symbol);
                yield fx != null ? fx.buyPrice() : BigDecimal.ZERO;
            }

            case CRYPTO -> {
                MarketPriceLatestDto crypto = getLatestCrypto().get(symbol);
                yield crypto != null ? crypto.buyPrice() : BigDecimal.ZERO;
            }

            case METAL -> {
                MarketPriceLatestDto metal = getLatestMetals().get(symbol);
                yield metal != null ? metal.buyPrice() : BigDecimal.ZERO;
            }

            case FUND -> {
                MarketPriceLatestDto fund = getLatestFunds().get(symbol);
                yield fund != null ? fund.buyPrice() : BigDecimal.ZERO;
            }

            default -> BigDecimal.ZERO;
        };
    }
}