package com.nurseli.nrsfinanceportal.integration.portfolio;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
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

class ManualPortfolioIntegrationTest extends FinanceIntegrationTestBase {

    @Autowired
    private ManualPortfolioPositionRepository positionRepository;

    @Test
    void createManualPosition_persistsAndListsForCurrentUser() throws Exception {
        long before = positionRepository.count();

        mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "THYAO",
                                  "quantity": 10,
                                  "buyPrice": 285.50,
                                  "buyPriceOverride": true,
                                  "buyFee": 0,
                                  "buyDate": "2024-06-01",
                                  "note": "integration-test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.symbol").value("THYAO"))
                .andExpect(jsonPath("$.data.quantity").value(10));

        assertThat(positionRepository.count()).isEqualTo(before + 1);

        mockMvc.perform(get("/api/portfolio/manual/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.symbol == 'THYAO')]").exists());
    }

    @Test
    void closeManualPosition_updatesStatusInDatabase() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "ASELS",
                                  "quantity": 5,
                                  "buyPrice": 50.00,
                                  "buyPriceOverride": true,
                                  "buyDate": "2024-01-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String body = created.getResponse().getContentAsString();
        long id = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");

        mockMvc.perform(post("/api/portfolio/manual/{id}/close", id)
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sellDate": "2024-12-01",
                                  "sellPrice": 55.00,
                                  "sellPriceOverride": true,
                                  "sellFee": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SOLD"));

        assertThat(positionRepository.findById(id))
                .isPresent()
                .get()
                .extracting(p -> p.getStatus().name())
                .isEqualTo("SOLD");
    }

    @Test
    void updateManualPosition_persistsChanges() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "GARAN",
                                  "quantity": 20,
                                  "buyPrice": 100.00,
                                  "buyPriceOverride": true,
                                  "buyDate": "2024-03-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/portfolio/manual/{id}", id)
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "GARAN",
                                  "quantity": 25,
                                  "buyPrice": 105.00,
                                  "buyPriceOverride": true,
                                  "buyDate": "2024-03-01",
                                  "note": "updated-in-it"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(25))
                .andExpect(jsonPath("$.data.note").value("updated-in-it"));

        assertThat(positionRepository.findById(id))
                .isPresent()
                .get()
                .extracting(p -> p.getQuantity().intValue())
                .isEqualTo(25);
    }

    @Test
    void deleteManualPosition_removesFromDatabase() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "SISE",
                                  "quantity": 3,
                                  "buyPrice": 40.00,
                                  "buyPriceOverride": true,
                                  "buyDate": "2024-04-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete("/api/portfolio/manual/{id}", id).with(integrationUserJwt()))
                .andExpect(status().isOk());

        assertThat(positionRepository.findById(id)).isEmpty();
    }

    @Test
    void manualSummary_returnsEnvelopeAfterCreate() throws Exception {
        mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "EREGL",
                                  "quantity": 8,
                                  "buyPrice": 45.00,
                                  "buyPriceOverride": true,
                                  "buyDate": "2024-05-01"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/portfolio/manual/summary/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }
}
