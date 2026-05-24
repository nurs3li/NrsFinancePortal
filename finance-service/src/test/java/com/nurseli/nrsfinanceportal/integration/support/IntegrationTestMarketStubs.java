package com.nurseli.nrsfinanceportal.integration.support;

import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Integration testlerde market-data HTTP çağrılarını pasif stub'lar.
 */
public final class IntegrationTestMarketStubs {

    private IntegrationTestMarketStubs() {
    }

    public static void stubPassiveMarketData(MarketDataClient marketDataClient) {
        lenient().when(marketDataClient.getViopLatestRows()).thenReturn(List.of());
        lenient().when(marketDataClient.getDebtLatestRows()).thenReturn(List.of());
        lenient().when(marketDataClient.getBistLatestRows()).thenReturn(List.of());
        lenient().when(marketDataClient.loadLatestPricing())
                .thenReturn(new MarketDataClient.LatestPricingSnapshot(
                        Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        lenient().when(marketDataClient.loadCpiIndexLookup(any(), any()))
                .thenReturn(CpiIndexLookup.empty());
    }
}
