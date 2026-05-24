package com.nurseli.nrsfinanceportal.integration.viopbond;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualViopPositionRepository;
import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import com.nurseli.nrsfinanceportal.integration.support.IntegrationTestJson;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManualViopPositionIntegrationTest extends FinanceIntegrationTestBase {

    @Autowired
    private ManualViopPositionRepository viopPositionRepository;

    @Test
    void createListAndCloseViopPosition_roundTrip() throws Exception {
        long before = viopPositionRepository.count();

        MvcResult created = mockMvc.perform(post("/api/me/viop-positions")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "F_XU0300424",
                                  "viopCategory": "INDEX",
                                  "direction": "LONG",
                                  "contractCount": 2,
                                  "entryPrice": 9500.00,
                                  "entryDate": "2024-06-01",
                                  "contractMultiplier": 10,
                                  "initialMargin": 5000.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("F_XU0300424"))
                .andReturn();

        assertThat(viopPositionRepository.count()).isEqualTo(before + 1);

        mockMvc.perform(get("/api/me/viop-positions").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.symbol == 'F_XU0300424')]").exists());

        long id = IntegrationTestJson.readLongId(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/me/viop-positions/{id}/close", id)
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "closePrice": 9600.00,
                                  "closeDate": "2024-07-01",
                                  "fee": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        assertThat(viopPositionRepository.findById(id))
                .isPresent()
                .get()
                .extracting(p -> p.getStatus().name())
                .isEqualTo("CLOSED");
    }

    @Test
    void viopSummary_returnsAfterOpenPosition() throws Exception {
        mockMvc.perform(post("/api/me/viop-positions")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "F_USDTRY0124",
                                  "viopCategory": "FX",
                                  "direction": "SHORT",
                                  "contractCount": 1,
                                  "entryPrice": 32.50,
                                  "entryDate": "2024-08-01",
                                  "contractMultiplier": 1000,
                                  "initialMargin": 2000.00
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/viop-positions/summary").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openPositionCount").isNumber());
    }
}
