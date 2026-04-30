package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.PriceQuality;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BankFxProviderTest {

    @Test
    void shouldFallbackToEvdsLatestWhenBankEmpty() {
        VakifBankFxProvider vakif = mock(VakifBankFxProvider.class);
        EvdsFxProvider evds = mock(EvdsFxProvider.class);
        when(vakif.getLatest()).thenReturn(Map.of());
        when(evds.getLatest()).thenReturn(Map.of("USDTRY", row("EVDS")));

        BankFxProvider provider = new BankFxProvider(vakif, evds);
        ReflectionTestUtils.setField(provider, "bankProxyEvdsEnabled", true);

        Map<String, MarketPriceLatestResponse> latest = provider.getLatest();
        assertEquals("EVDS", latest.get("USDTRY").source());
    }

    @Test
    void shouldReturnBankHistoryWhenAvailable() {
        VakifBankFxProvider vakif = mock(VakifBankFxProvider.class);
        EvdsFxProvider evds = mock(EvdsFxProvider.class);
        when(vakif.getHistory("USDTRY", 7)).thenReturn(List.of(history("VAKIFBANK")));

        BankFxProvider provider = new BankFxProvider(vakif, evds);
        ReflectionTestUtils.setField(provider, "bankProxyEvdsEnabled", true);

        List<MarketPriceHistoryResponse> history = provider.getHistory("USDTRY", 7);
        assertEquals("VAKIFBANK", history.get(0).source());
    }

    private static MarketPriceLatestResponse row(String source) {
        LocalDateTime now = LocalDateTime.now();
        return new MarketPriceLatestResponse(
                "USDTRY",
                new BigDecimal("39.1"),
                new BigDecimal("39.2"),
                source,
                now,
                now,
                PriceQuality.FALLBACK,
                null,
                null,
                null
        );
    }

    private static MarketPriceHistoryResponse history(String source) {
        LocalDateTime now = LocalDateTime.now();
        return new MarketPriceHistoryResponse(
                new BigDecimal("39.1"),
                new BigDecimal("39.2"),
                now,
                source,
                now,
                com.nurseli.marketdata.api.dto.DataQualityFlag.EXACT
        );
    }
}
