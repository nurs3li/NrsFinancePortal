package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.FxPriceDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarketDataClient {

    private final WebClient marketDataWebClient;

    public Map<String, FxPriceDto> getLatestDoviz() {
        return marketDataWebClient.get()
                .uri("/api/market/doviz/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, FxPriceDto>>() {})
                .block();
    }

    public Map<String, MarketPriceLatestDto> getLatestMetals() {
        return marketDataWebClient.get()
                .uri("/api/market/metals/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, MarketPriceLatestDto>>() {})
                .block();
    }

    public Map<String, MarketPriceLatestDto> getLatestCrypto() {
        return marketDataWebClient.get()
                .uri("/api/market/crypto/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, MarketPriceLatestDto>>() {})
                .block();
    }

    public Map<String, MarketPriceLatestDto> getLatestFunds() {
        return marketDataWebClient.get()
                .uri("/api/market/funds/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, MarketPriceLatestDto>>() {})
                .block();
    }

    public Map<String, MarketPriceLatestDto> getLatestEquity() {
        return marketDataWebClient.get()
                .uri("/api/market/equity/latest")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, MarketPriceLatestDto>>() {})
                .block();
    }

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
            case STOCK -> {
                MarketPriceLatestDto equity = getLatestEquity().get(symbol);
                if (equity == null) yield BigDecimal.ZERO;
                BigDecimal usdPrice = equity.buyPrice();
                FxPriceDto usdTry = getLatestDoviz().get("USDTRY");
                if (usdTry == null) yield usdPrice;
                yield usdPrice.multiply(usdTry.buyPrice());
            }
            default -> BigDecimal.ZERO;
        };
    }

    public List<MarketPriceHistoryDto> getHistory(AssetType type, String symbol, int days) {
        String uri = switch (type) {
            case FX -> "/api/market/doviz/history?symbol={symbol}&days={days}";
            case CRYPTO -> "/api/market/crypto/history?symbol={symbol}&days={days}";
            case METAL -> "/api/market/metals/history?symbol={symbol}&days={days}";
            case FUND -> "/api/market/funds/history?symbol={symbol}&days={days}";
            case STOCK -> throw new IllegalStateException("STOCK history endpoint is not available");
            default -> throw new IllegalStateException("Unsupported asset type: " + type);
        };

        return marketDataWebClient.get()
                .uri(uri, symbol, days)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<MarketPriceHistoryDto>>() {})
                .block();
    }
}