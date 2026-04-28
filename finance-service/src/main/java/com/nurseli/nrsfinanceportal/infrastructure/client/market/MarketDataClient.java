package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarketDataClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(18);

    /** Döviz dahil tüm latest uçları market-data {@code MarketPriceLatestResponse} ile aynı şema. */
    private static final ParameterizedTypeReference<Map<String, MarketPriceLatestDto>> LATEST_MAP =
            new ParameterizedTypeReference<>() {};

    private final WebClient marketDataWebClient;

    /**
     * Tüm "latest" haritalarını tek seferde (paralel) çeker. Portföy performansı gibi
     * çoklu sembol döngülerinde her satır için ayrı HTTP yapılmasını önler.
     */
    public LatestPricingSnapshot loadLatestPricing() {
        Mono<Map<String, MarketPriceLatestDto>> fx = latestMono("/api/market/doviz/latest", LATEST_MAP);
        Mono<Map<String, MarketPriceLatestDto>> metals = latestMono("/api/market/metals/latest", LATEST_MAP);
        Mono<Map<String, MarketPriceLatestDto>> crypto = latestMono("/api/market/crypto/latest", LATEST_MAP);
        Mono<Map<String, MarketPriceLatestDto>> funds = latestMono("/api/market/funds/latest", LATEST_MAP);
        Mono<Map<String, MarketPriceLatestDto>> equity = latestMono("/api/market/equity/latest", LATEST_MAP);

        return Mono.zip(fx, metals, crypto, funds, equity)
                .map(t -> new LatestPricingSnapshot(
                        emptyMap(t.getT1()),
                        emptyMap(t.getT2()),
                        emptyMap(t.getT3()),
                        emptyMap(t.getT4()),
                        emptyMap(t.getT5())
                ))
                .timeout(REQUEST_TIMEOUT.plusSeconds(2))
                .onErrorReturn(LatestPricingSnapshot.empty())
                .block();
    }

    private <T> Mono<Map<String, T>> latestMono(String uri, ParameterizedTypeReference<Map<String, T>> ref) {
        return marketDataWebClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(ref)
                .timeout(REQUEST_TIMEOUT)
                .onErrorReturn(Map.of());
    }

    private static <T> Map<String, T> emptyMap(Map<String, T> m) {
        return m != null ? m : Map.of();
    }

    private <T> Map<String, T> blockLatest(String uri, ParameterizedTypeReference<Map<String, T>> ref) {
        return emptyMap(latestMono(uri, ref).block(REQUEST_TIMEOUT.plusSeconds(1)));
    }

    public Map<String, MarketPriceLatestDto> getLatestDoviz() {
        return blockLatest("/api/market/doviz/latest", LATEST_MAP);
    }

    public Map<String, MarketPriceLatestDto> getLatestMetals() {
        return blockLatest("/api/market/metals/latest", LATEST_MAP);
    }

    public Map<String, MarketPriceLatestDto> getLatestCrypto() {
        return blockLatest("/api/market/crypto/latest", LATEST_MAP);
    }

    public Map<String, MarketPriceLatestDto> getLatestFunds() {
        return blockLatest("/api/market/funds/latest", LATEST_MAP);
    }

    public Map<String, MarketPriceLatestDto> getLatestEquity() {
        return blockLatest("/api/market/equity/latest", LATEST_MAP);
    }

    public BigDecimal getPriceTry(AssetType type, String symbol) {
        return getPriceTry(type, symbol, loadLatestPricing());
    }

    public BigDecimal getPriceTry(AssetType type, String symbol, LatestPricingSnapshot snap) {
        return switch (type) {
            case FX -> {
                MarketPriceLatestDto fx = snap.fx().get(symbol);
                yield fx != null ? nz(fx.buyPrice()) : BigDecimal.ZERO;
            }
            case CRYPTO -> {
                MarketPriceLatestDto crypto = snap.crypto().get(symbol);
                if (crypto == null) yield BigDecimal.ZERO;
                yield usdToTry(crypto.buyPrice(), snap);
            }
            case METAL -> {
                MarketPriceLatestDto metal = snap.metals().get(symbol);
                yield metal != null ? nz(metal.buyPrice()) : BigDecimal.ZERO;
            }
            case FUND -> {
                MarketPriceLatestDto fund = snap.funds().get(symbol);
                if (fund == null) yield BigDecimal.ZERO;
                yield usdToTry(fund.buyPrice(), snap);
            }
            case STOCK -> {
                MarketPriceLatestDto equity = snap.equity().get(symbol);
                if (equity == null) yield BigDecimal.ZERO;
                yield usdToTry(equity.buyPrice(), snap);
            }
            default -> BigDecimal.ZERO;
        };
    }

    private BigDecimal usdToTry(BigDecimal usdPrice, LatestPricingSnapshot snap) {
        if (usdPrice == null) return BigDecimal.ZERO;
        MarketPriceLatestDto usdTry = snap.fx().get("USDTRY");
        if (usdTry == null || usdTry.buyPrice() == null || usdTry.buyPrice().signum() <= 0) {
            return usdPrice;
        }
        return usdPrice.multiply(usdTry.buyPrice());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public List<MarketPriceHistoryDto> getHistory(AssetType type, String symbol, int days) {
        String uri = switch (type) {
            case FX -> "/api/market/doviz/history?symbol={symbol}&days={days}";
            case CRYPTO -> "/api/market/crypto/history?symbol={symbol}&days={days}";
            case METAL -> "/api/market/metals/history?symbol={symbol}&days={days}";
            case FUND -> "/api/market/funds/history?symbol={symbol}&days={days}";
            case STOCK -> "/api/market/equity/history?symbol={symbol}&days={days}";
            default -> throw new IllegalStateException("Unsupported asset type: " + type);
        };

        List<MarketPriceHistoryDto> list = marketDataWebClient.get()
                .uri(uri, symbol, days)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<MarketPriceHistoryDto>>() {})
                .timeout(REQUEST_TIMEOUT)
                .onErrorReturn(List.of())
                .block(REQUEST_TIMEOUT.plusSeconds(1));
        return list != null ? list : List.of();
    }

    public record LatestPricingSnapshot(
            Map<String, MarketPriceLatestDto> fx,
            Map<String, MarketPriceLatestDto> metals,
            Map<String, MarketPriceLatestDto> crypto,
            Map<String, MarketPriceLatestDto> funds,
            Map<String, MarketPriceLatestDto> equity
    ) {
        static LatestPricingSnapshot empty() {
            return new LatestPricingSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
