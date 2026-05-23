package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FxProviderFacadeTest {

    @Test
    void tcmbOkShouldReturnExact() {
        ProviderRegistry registry = Mockito.mock(ProviderRegistry.class);
        MarketPriceQueryService queryService = Mockito.mock(MarketPriceQueryService.class);
        FxProvider tcmb = provider("TCMB", Map.of("USDTRY", row("TCMB")));
        Mockito.when(registry.fxCanonical()).thenReturn(tcmb);
        Mockito.when(registry.fxFallbackOrder()).thenReturn(List.of());

        FxProviderFacade facade = new FxProviderFacade(registry, queryService, new SimpleMeterRegistry());
        MarketPriceLatestResponse response = facade.getLatest("USDTRY");

        assertEquals(PriceQuality.EXACT, response.quality());
    }

    @Test
    void tcmbFailEvdsOkShouldReturnFallback() {
        ProviderRegistry registry = Mockito.mock(ProviderRegistry.class);
        MarketPriceQueryService queryService = Mockito.mock(MarketPriceQueryService.class);
        FxProvider tcmb = provider("TCMB", Map.of());
        FxProvider evds = provider("EVDS", Map.of("USDTRY", row("EVDS")));
        Mockito.when(registry.fxCanonical()).thenReturn(tcmb);
        Mockito.when(registry.fxFallbackOrder()).thenReturn(List.of(evds));

        FxProviderFacade facade = new FxProviderFacade(registry, queryService, new SimpleMeterRegistry());
        MarketPriceLatestResponse response = facade.getLatest("USDTRY");

        assertEquals(PriceQuality.FALLBACK, response.quality());
        assertEquals("EVDS", response.source());
    }

    @Test
    void bothFailShouldReturnStaleFromDb() {
        ProviderRegistry registry = Mockito.mock(ProviderRegistry.class);
        MarketPriceQueryService queryService = Mockito.mock(MarketPriceQueryService.class);
        FxProvider tcmb = provider("TCMB", Map.of());
        FxProvider evds = provider("EVDS", Map.of());
        Mockito.when(registry.fxCanonical()).thenReturn(tcmb);
        Mockito.when(registry.fxFallbackOrder()).thenReturn(List.of(evds));
        Mockito.when(queryService.getLatestOrThrow("USDTRY")).thenReturn(row("DB"));

        FxProviderFacade facade = new FxProviderFacade(registry, queryService, new SimpleMeterRegistry());
        MarketPriceLatestResponse response = facade.getLatest("USDTRY");

        assertEquals(PriceQuality.STALE, response.quality());
    }

    private FxProvider provider(String name, Map<String, MarketPriceLatestResponse> rows) {
        return new FxProvider() {
            @Override public Map<String, MarketPriceLatestResponse> getLatest() { return rows; }
            @Override public java.util.List<com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse> getHistory(String symbol, int days) { return List.of(); }
            @Override public String providerName() { return name; }
            @Override public boolean isCanonical() { return "TCMB".equals(name); }
        };
    }

    private MarketPriceLatestResponse row(String source) {
        LocalDateTime now = LocalDateTime.now();
        return new MarketPriceLatestResponse("USDTRY", new BigDecimal("39.1"), new BigDecimal("39.2"), source, now, now, PriceQuality.EXACT, null, null, null);
    }
}
