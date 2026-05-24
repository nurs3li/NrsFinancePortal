package com.nurseli.marketdata.integration;

import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.infrastructure.persistence.LoanRateWeeklyObservationRepository;
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

class LoanRatesIntegrationTest extends MarketdataIntegrationTestBase {

    @Autowired
    private LoanRateWeeklyObservationRepository loanRateWeeklyObservationRepository;

    @BeforeEach
    void seedLoanRates() {
        LocalDate today = LocalDate.now();
        loanRateWeeklyObservationRepository.save(
                MarketdataIntegrationFixtures.loanRate(
                        LoanRateSubtype.CONSUMER_TRY,
                        today.minusDays(7),
                        new BigDecimal("58.50")
                )
        );
        loanRateWeeklyObservationRepository.save(
                MarketdataIntegrationFixtures.loanRate(
                        LoanRateSubtype.CONSUMER_TRY,
                        today,
                        new BigDecimal("61.55")
                )
        );
    }

    @Test
    void loanRatesLatest_returnsFreshConsumerRateFromDatabase() throws Exception {
        mockMvc.perform(get("/api/market/macro/loan-rates/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type == 'CONSUMER_TRY')].value").value(61.55));
    }

    @Test
    void loanRatesHistory_returnsPersistedPoints() throws Exception {
        LocalDate from = LocalDate.now().minusDays(14);
        LocalDate to = LocalDate.now();

        mockMvc.perform(get("/api/market/macro/loan-rates/history")
                        .param("types", "CONSUMER_TRY")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series[0].type").value("CONSUMER_TRY"))
                .andExpect(jsonPath("$.data.series[0].points.length()").value(2));
    }
}
