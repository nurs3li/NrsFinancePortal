package com.nurseli.nrsfinanceportal.integration.portfolio;

import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioUnifiedIntegrationTest extends FinanceIntegrationTestBase {

    @Test
    void unifiedPortfolio_includesManualPositionAfterCreate() throws Exception {
        mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "KCHOL",
                                  "quantity": 4,
                                  "buyPrice": 180.00,
                                  "buyPriceOverride": true,
                                  "buyDate": "2024-07-01"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portfolio/me/unified").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.symbol == 'KCHOL')]").exists());
    }
}
