package com.nurseli.marketdata.integration;

import com.nurseli.marketdata.integration.support.MarketdataIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminInflationSecurityIntegrationTest extends MarketdataIntegrationTestBase {

    @Test
    void inflationBackfill_withoutJwt_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/market/inflation/backfill")
                        .param("from", "2024-01-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inflationBackfill_withUserJwt_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/market/inflation/backfill")
                        .param("from", "2024-01-01")
                        .with(integrationUserJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void inflationBackfill_withAdminJwt_invokesService() throws Exception {
        mockMvc.perform(post("/api/admin/market/inflation/backfill")
                        .param("from", "2024-01-01")
                        .with(integrationAdminJwt()))
                .andExpect(status().isOk());
    }
}
