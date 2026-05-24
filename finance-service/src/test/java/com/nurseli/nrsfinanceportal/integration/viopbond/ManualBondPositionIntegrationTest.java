package com.nurseli.nrsfinanceportal.integration.viopbond;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualBondPositionRepository;
import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManualBondPositionIntegrationTest extends FinanceIntegrationTestBase {

    @Autowired
    private ManualBondPositionRepository bondPositionRepository;

    @Test
    void createListAndSellBondPosition_roundTrip() throws Exception {
        long before = bondPositionRepository.count();

        MvcResult created = mockMvc.perform(post("/api/me/bond-positions")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "TR0001234567",
                                  "bondType": "GOVERNMENT_BOND",
                                  "currency": "TRY",
                                  "nominalValue": 100000,
                                  "buyPrice": 98.50,
                                  "buyDate": "2024-01-10",
                                  "currentPrice": 99.00,
                                  "couponRate": 12.5,
                                  "couponFrequency": "SEMI_ANNUAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("TR0001234567"))
                .andReturn();

        assertThat(bondPositionRepository.count()).isEqualTo(before + 1);

        mockMvc.perform(get("/api/me/bond-positions").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.symbol == 'TR0001234567')]").exists());

        long id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/me/bond-positions/{id}/sell", id)
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sellPrice": 101.00,
                                  "sellDate": "2024-09-01",
                                  "collectedCouponAmount": 500,
                                  "fee": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SOLD"));

        assertThat(bondPositionRepository.findById(id))
                .isPresent()
                .get()
                .extracting(p -> p.getStatus().name())
                .isEqualTo("SOLD");
    }

    @Test
    void bondSummary_returnsAfterOpenPosition() throws Exception {
        mockMvc.perform(post("/api/me/bond-positions")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "TR0009876543",
                                  "bondType": "EUROBOND",
                                  "currency": "USD",
                                  "nominalValue": 50000,
                                  "buyPrice": 95.00,
                                  "buyDate": "2024-02-01",
                                  "currentPrice": 96.00
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/bond-positions/summary").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openPositionCount").isNumber());
    }
}
