package com.nurseli.nrsfinanceportal.integration.pricealert;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.PriceAlertRepository;
import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PriceAlertIntegrationTest extends FinanceIntegrationTestBase {

    @Autowired
    private PriceAlertRepository priceAlertRepository;

    @Test
    void createListAndDeletePriceAlert_roundTrip() throws Exception {
        long before = priceAlertRepository.count();

        MvcResult created = mockMvc.perform(post("/api/me/price-alerts")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetType": "BIST",
                                  "symbol": "THYAO",
                                  "conditionType": "PRICE_GTE",
                                  "threshold": 300,
                                  "channels": "BOTH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("THYAO"))
                .andReturn();

        assertThat(priceAlertRepository.count()).isEqualTo(before + 1);

        mockMvc.perform(get("/api/me/price-alerts").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.symbol == 'THYAO')]").exists());

        long alertId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/me/price-alerts/{id}", alertId).with(integrationUserJwt()))
                .andExpect(status().isOk());

        assertThat(priceAlertRepository.count()).isEqualTo(before);
    }

    @Test
    void updatePriceAlert_persistsThresholdChange() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/me/price-alerts")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assetType": "BIST",
                                  "symbol": "ASELS",
                                  "conditionType": "PRICE_GTE",
                                  "threshold": 100,
                                  "channels": "BOTH"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long alertId = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/me/price-alerts/{id}", alertId)
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "threshold": 150
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.threshold").value(150));

        assertThat(priceAlertRepository.findById(alertId))
                .isPresent()
                .get()
                .extracting(a -> a.getThreshold().intValue())
                .isEqualTo(150);
    }
}
