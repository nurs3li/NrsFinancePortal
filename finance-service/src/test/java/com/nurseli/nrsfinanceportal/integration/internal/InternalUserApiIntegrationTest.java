package com.nurseli.nrsfinanceportal.integration.internal;

import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalUserApiIntegrationTest extends FinanceIntegrationTestBase {

    @Test
    void bySub_returnsUserInfoForExistingKeycloakId() throws Exception {
        mockMvc.perform(get("/internal/users/by-sub/{sub}", TEST_KEYCLOAK_SUB).with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(TEST_KEYCLOAK_SUB))
                .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    @Test
    void bySub_returns404ForUnknownUser() throws Exception {
        mockMvc.perform(get("/internal/users/by-sub/{sub}", "unknown-sub-xyz").with(integrationUserJwt()))
                .andExpect(status().isNotFound());
    }
}
