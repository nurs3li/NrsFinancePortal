package com.nurseli.nrsfinanceportal.domain.pricing;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MarketPriceLookupService implements PriceLookupService {

    private final MarketDataClient marketDataClient;

    @Override
    public BigDecimal getTryPrice(AssetType type, String symbol) {

        // 🔑 TEK EKLENEN SATIR
        String normalizedSymbol =
                SymbolNormalizer.normalize(type, symbol);

        return switch (type) {

            case FX ->
                    marketDataClient.getLatestDoviz()
                            .get(normalizedSymbol)
                            .buyPrice();

            case METAL ->
                    marketDataClient.getLatestMetals()
                            .get(normalizedSymbol)
                            .buyPrice();

            case FUND ->
                    marketDataClient.getLatestFunds()
                            .get(normalizedSymbol)
                            .buyPrice();

            case CRYPTO -> {
                BigDecimal usdPrice =
                        marketDataClient.getLatestCrypto()
                                .get(normalizedSymbol)
                                .buyPrice();

                BigDecimal usdTry =
                        marketDataClient.getLatestDoviz()
                                .get("USDTRY")
                                .buyPrice();

                yield usdPrice.multiply(usdTry);
            }

            default ->
                    throw new UnsupportedOperationException("Not supported yet: " + type);
        };
    }
}
