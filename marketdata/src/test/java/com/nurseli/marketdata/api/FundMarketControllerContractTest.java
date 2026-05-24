package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.application.provider.FundProvider;
import com.nurseli.marketdata.application.provider.ProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FundMarketController.class)
@AutoConfigureMockMvc(addFilters = false)
class FundMarketControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProviderRegistry providerRegistry;

    @MockitoBean
    private FundProvider fundProvider;

    @Test
    void latestShouldReturnCanonicalProviderDataWithSource() throws Exception {
        when(providerRegistry.fundCanonical()).thenReturn(fundProvider);
        when(providerRegistry.fundFallbackOrder()).thenReturn(List.of());
        when(fundProvider.getLatest()).thenReturn(Map.of(
                "SPY",
                new MarketPriceLatestResponse(
                        "SPY",
                        new BigDecimal("100.00"),
                        new BigDecimal("101.00"),
                        "ETF",
                        LocalDateTime.now(),
                        LocalDateTime.now(),
                        PriceQuality.EXACT,
                        null,
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/api/market/funds/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.SPY.source").value("ETF"));
    }
}
