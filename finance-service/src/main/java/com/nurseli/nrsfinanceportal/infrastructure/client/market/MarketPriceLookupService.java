package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricing.PriceLookupService;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Market data servisinden fiyat çeken PriceLookupService uygulaması.
 */
@Service
@RequiredArgsConstructor
public class MarketPriceLookupService implements PriceLookupService {

    private final MarketDataClient marketDataClient;

    @Override
    /**
 * MarketDataClient üzerinden güncel TRY fiyatını çözümler.
 */
    public BigDecimal getTryPrice(AssetType type, String symbol) {

        String normalizedSymbol = SymbolNormalizer.normalize(type, symbol);
        var snap = marketDataClient.loadLatestPricing();
        return marketDataClient.getPriceTry(type, normalizedSymbol, snap);
    }
}