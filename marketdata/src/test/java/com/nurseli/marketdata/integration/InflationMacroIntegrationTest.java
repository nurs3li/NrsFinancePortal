package com.nurseli.marketdata.integration;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.infrastructure.persistence.InflationIndexMonthlyRepository;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationFixtures;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InflationMacroIntegrationTest extends MarketdataIntegrationTestBase {

    @Autowired
    private InflationIndexMonthlyRepository inflationIndexMonthlyRepository;

    @BeforeEach
    void seedInflationAndStubCpiLatest() {
        inflationIndexMonthlyRepository.save(
                MarketdataIntegrationFixtures.inflationMonth(
                        InflationIndicatorType.CPI,
                        MarketdataIntegrationFixtures.CPI_SERIES_CODE,
                        YearMonth.of(2024, 1),
                        new BigDecimal("1500.00")
                )
        );
        inflationIndexMonthlyRepository.save(
                MarketdataIntegrationFixtures.inflationMonth(
                        InflationIndicatorType.CPI,
                        MarketdataIntegrationFixtures.CPI_SERIES_CODE,
                        YearMonth.of(2024, 2),
                        new BigDecimal("1525.00")
                )
        );
        inflationIndexMonthlyRepository.save(
                MarketdataIntegrationFixtures.inflationMonth(
                        InflationIndicatorType.PPI,
                        MarketdataIntegrationFixtures.PPI_SERIES_CODE,
                        YearMonth.of(2024, 2),
                        new BigDecimal("2100.00")
                )
        );

        when(evdsCpiTrService.latestCpiTr()).thenReturn(Optional.of(
                new CpiTrMacroResponse(
                        MarketdataIntegrationFixtures.CPI_SERIES_CODE,
                        LocalDate.of(2024, 2, 1),
                        new BigDecimal("1525.00"),
                        new BigDecimal("1.67"),
                        new BigDecimal("45.00"),
                        null
                )
        ));
    }

    @Test
    void inflationHistory_readsPersistedCpiRows() throws Exception {
        mockMvc.perform(get("/api/market/macro/inflation/history")
                        .param("type", "CPI")
                        .param("from", "2024-01")
                        .param("to", "2024-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indicatorType").value("CPI"))
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.rows[0].indexValue").value(1500.00));
    }

    @Test
    void inflationCompare_returnsCpiAndPpiSeries() throws Exception {
        mockMvc.perform(get("/api/market/macro/inflation/compare")
                        .param("from", "2024-01")
                        .param("to", "2024-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(2));
    }

    @Test
    void inflationLatest_usesStubbedCpiSnapshot() throws Exception {
        mockMvc.perform(get("/api/market/macro/inflation/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cpi.seriesCode").value(MarketdataIntegrationFixtures.CPI_SERIES_CODE))
                .andExpect(jsonPath("$.data.cpi.indexValue").value(1525.00));
    }

    @Test
    void inflationHistory_invalidType_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/market/macro/inflation/history")
                        .param("type", "INVALID")
                        .param("from", "2024-01")
                        .param("to", "2024-02"))
                .andExpect(status().isBadRequest());
    }
}
