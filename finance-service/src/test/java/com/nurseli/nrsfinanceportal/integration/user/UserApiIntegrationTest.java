package com.nurseli.nrsfinanceportal.integration.user;

import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserApiIntegrationTest extends FinanceIntegrationTestBase {

    @Test
    void getCurrentUser_returnsProfileFromDatabase() throws Exception {
        mockMvc.perform(get("/api/users/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.username").value(TEST_USERNAME));
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users").with(integrationAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[?(@.email == '" + TEST_EMAIL + "')]").exists());
    }
}
