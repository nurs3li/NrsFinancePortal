package com.nurseli.nrsfinanceportal.integration.user;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserStarredAssetRepository;
import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserStarredAssetIntegrationTest extends FinanceIntegrationTestBase {

    @Autowired
    private UserStarredAssetRepository starredAssetRepository;

    @Test
    void updateStarredAssets_persistsAndReturnsSelection() throws Exception {
        mockMvc.perform(put("/api/me/starred-assets")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selected": [
                                    { "marketType": "EQUITY", "symbol": "THYAO" },
                                    { "marketType": "FX", "symbol": "USDTRY" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.selected.length()").value(2));

        mockMvc.perform(get("/api/me/starred-assets").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.selected[?(@.symbol == 'THYAO')]").exists());

        assertThat(starredAssetRepository.findByUserIdOrderByPositionAsc(testUser.getId())).isNotEmpty();
    }
}
