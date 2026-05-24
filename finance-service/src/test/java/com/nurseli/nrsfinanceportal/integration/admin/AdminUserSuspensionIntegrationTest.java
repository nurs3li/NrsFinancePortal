package com.nurseli.nrsfinanceportal.integration.admin;

import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserSuspensionIntegrationTest extends FinanceIntegrationTestBase {

    @Test
    void suspendAndUnsuspendLogin_updatesDatabaseFlags() throws Exception {
        mockMvc.perform(post("/api/admin/users/{userId}/suspend-login", testUser.getId())
                        .with(integrationAdminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "integration-test suspend" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.findById(testUser.getId()))
                .isPresent()
                .get()
                .extracting(u -> u.isLoginSuspended())
                .isEqualTo(true);

        mockMvc.perform(post("/api/admin/users/{userId}/unsuspend-login", testUser.getId())
                        .with(integrationAdminJwt()))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(testUser.getId()))
                .isPresent()
                .get()
                .extracting(u -> u.isLoginSuspended())
                .isEqualTo(false);
    }
}
