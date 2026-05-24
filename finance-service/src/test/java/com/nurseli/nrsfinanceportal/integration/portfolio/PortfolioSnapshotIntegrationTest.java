package com.nurseli.nrsfinanceportal.integration.portfolio;

import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioSnapshotIntegrationTest extends FinanceIntegrationTestBase {

    @Test
    void mySnapshots_returnsEnvelopeForDateRange() throws Exception {
        Instant to = Instant.now();
        Instant from = to.minus(30, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/portfolio/snapshots/me")
                        .with(integrationUserJwt())
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }
}
