package com.nurseli.nrsfinanceportal.domain.pricing;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceLatestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MarketPriceLookupService implements PriceLookupService {

    private final MarketDataClient marketDataClient;

    @Override
    public BigDecimal getTryPrice(AssetType type, String symbol) {

        String normalizedSymbol = SymbolNormalizer.normalize(type, symbol);

        return switch (type) {

            case FX -> {
                var fx = marketDataClient.getLatestDoviz().get(normalizedSymbol);
                yield fx != null ? fx.buyPrice() : BigDecimal.ZERO;
            }

            case METAL -> {
                var metal = marketDataClient.getLatestMetals().get(normalizedSymbol);
                yield metal != null ? metal.buyPrice() : BigDecimal.ZERO;
            }

            case FUND -> {
                var fund = marketDataClient.getLatestFunds().get(normalizedSymbol);
                yield fund != null ? fund.buyPrice() : BigDecimal.ZERO;
            }

            case CRYPTO -> {
                var crypto = marketDataClient.getLatestCrypto().get(normalizedSymbol);
                if (crypto == null) yield BigDecimal.ZERO;
                BigDecimal usdPrice = crypto.buyPrice();
                var usdTry = marketDataClient.getLatestDoviz().get("USDTRY");
                if (usdTry == null) yield BigDecimal.ZERO;
                yield usdPrice.multiply(usdTry.buyPrice());
            }

            case STOCK -> {
                var equity = marketDataClient.getLatestEquity().get(normalizedSymbol);
                if (equity == null) yield BigDecimal.ZERO;
                BigDecimal usdPrice = equity.buyPrice();
                var usdTry = marketDataClient.getLatestDoviz().get("USDTRY");
                if (usdTry == null) yield usdPrice;
                yield usdPrice.multiply(usdTry.buyPrice());
            }

            default ->
                    throw new UnsupportedOperationException("Not supported yet: " + type);
        };
    }
}