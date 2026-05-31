package com.nurseli.nrsfinanceportal.integration.support;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**
 * Integration testlerde market-data HTTP çağrılarını pasif stub'lar.
 */
public final class IntegrationTestMarketStubs {

    private static final Map<String, BigDecimal> BIST_WARMUP_LATEST = Map.of(
            "THYAO", new BigDecimal("300.00"),
            "GARAN", new BigDecimal("100.00")
    );

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

    /**
     * Manuel portföy warmup/timeseries testleri için THYAO ve GARAN BIST fiyat geçmişi + latest stub'ları.
     */
    public static void stubBistPortfolioWarmupSymbols(MarketDataClient marketDataClient) {
        stubBistSymbol(marketDataClient, "THYAO", BIST_WARMUP_LATEST.get("THYAO"));
        stubBistSymbol(marketDataClient, "GARAN", BIST_WARMUP_LATEST.get("GARAN"));
        lenient().when(marketDataClient.getBistLatestRows()).thenReturn(List.of(
                bistLatestRow("THYAO", BIST_WARMUP_LATEST.get("THYAO")),
                bistLatestRow("GARAN", BIST_WARMUP_LATEST.get("GARAN"))
        ));
        lenient().when(marketDataClient.getPriceTry(any(), anyString())).thenAnswer(invocation -> {
            AssetType type = invocation.getArgument(0);
            String symbol = invocation.getArgument(1);
            return bistLatestPrice(type, symbol);
        });
        lenient().when(marketDataClient.getPriceTry(any(), anyString(), any())).thenAnswer(invocation -> {
            AssetType type = invocation.getArgument(0);
            String symbol = invocation.getArgument(1);
            return bistLatestPrice(type, symbol);
        });
    }

    private static BigDecimal bistLatestPrice(AssetType type, String symbol) {
        if (type != AssetType.BIST || symbol == null || symbol.isBlank()) {
            return BigDecimal.ZERO;
        }
        String key = symbol.trim().toUpperCase();
        return BIST_WARMUP_LATEST.getOrDefault(key, BigDecimal.ZERO);
    }

    private static MarketDataClient.BistLatestRow bistLatestRow(String symbol, BigDecimal price) {
        return new MarketDataClient.BistLatestRow(
                symbol,
                symbol,
                null,
                price,
                price,
                null,
                null,
                "TEST",
                "OK");
    }

    private static void stubBistSymbol(MarketDataClient marketDataClient, String symbol, BigDecimal price) {
        lenient().when(marketDataClient.getBistBatchHistoryMapped(eq(symbol), any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate from = invocation.getArgument(1);
                    LocalDate to = invocation.getArgument(2);
                    List<MarketPriceHistoryDto> points = new ArrayList<>();
                    LocalDate cursor = from != null ? from : LocalDate.of(2020, 1, 1);
                    LocalDate end = to != null ? to : LocalDate.now();
                    while (!cursor.isAfter(end)) {
                        points.add(new MarketPriceHistoryDto(price, price, cursor.atStartOfDay()));
                        cursor = cursor.plusDays(7);
                    }
                    if (points.isEmpty()) {
                        points.add(new MarketPriceHistoryDto(price, price, end.atStartOfDay()));
                    }
                    return Map.of(symbol, points);
                });
    }
}
