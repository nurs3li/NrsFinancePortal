package com.nurseli.marketdata.integration;

import com.nurseli.marketdata.infrastructure.persistence.BankFxLatestRepository;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationFixtures;
import com.nurseli.marketdata.integration.support.MarketdataIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankRatesIntegrationTest extends MarketdataIntegrationTestBase {

    @Autowired
    private BankFxLatestRepository bankFxLatestRepository;

    @BeforeEach
    void seedBankRates() {
        bankFxLatestRepository.save(
                MarketdataIntegrationFixtures.bankFxUsd("AKBNK", "Akbank", new BigDecimal("34.10"), new BigDecimal("34.50"))
        );
        bankFxLatestRepository.save(
                MarketdataIntegrationFixtures.bankFxUsd("GARAN", "Garanti", new BigDecimal("34.05"), new BigDecimal("34.45"))
        );
    }

    @Test
    void bankRatesBoard_returnsUsdRowsFromDatabase() throws Exception {
        mockMvc.perform(get("/api/market/bank-rates/board").param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.rows[?(@.bankCode == 'AKBNK')].buy").value(34.10));
    }
}
