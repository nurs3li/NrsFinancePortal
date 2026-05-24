package com.nurseli.marketdata.integration;

import com.nurseli.marketdata.infrastructure.persistence.DepositRateObservationRepository;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationFixtures;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DepositRatesIntegrationTest extends MarketdataIntegrationTestBase {

    @Autowired
    private DepositRateObservationRepository depositRateObservationRepository;

    @BeforeEach
    void seedDepositRates() {
        LocalDate today = LocalDate.now();
        depositRateObservationRepository.save(
                MarketdataIntegrationFixtures.depositRate(today.minusWeeks(1), new BigDecimal("44.00"))
        );
        depositRateObservationRepository.save(
                MarketdataIntegrationFixtures.depositRate(today, new BigDecimal("45.25"))
        );
    }

    @Test
    void depositRatesLatest_returnsTryOneMonthRow() throws Exception {
        mockMvc.perform(get("/api/market/macro/deposit-rates/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.currency == 'TRY' && @.term == '1M')].ratePercent").value(45.25));
    }

    @Test
    void depositRatesHistory_returnsObservationRange() throws Exception {
        LocalDate from = LocalDate.now().minusWeeks(2);
        LocalDate to = LocalDate.now();

        mockMvc.perform(get("/api/market/macro/deposit-rates/history")
                        .param("currency", "TRY")
                        .param("term", "1M")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].ratePercent").value(45.25));
    }

    @Test
    void depositRatesSeries_listsTryTerms() throws Exception {
        mockMvc.perform(get("/api/market/macro/deposit-rates/series").param("currency", "TRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.term == '1M')].currency").value("TRY"));
    }
}
