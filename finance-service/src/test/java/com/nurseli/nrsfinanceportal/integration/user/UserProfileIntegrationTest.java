package com.nurseli.nrsfinanceportal.integration.user;

import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileIntegrationTest extends FinanceIntegrationTestBase {

    @Test
    void updateProfile_persistsFullNameInDatabase() throws Exception {
        mockMvc.perform(patch("/api/users/me/profile")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Integration",
                                  "lastName": "Tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("Integration"))
                .andExpect(jsonPath("$.data.lastName").value("Tester"));

        assertThat(userRepository.findById(testUser.getId()))
                .isPresent()
                .get()
                .satisfies(u -> {
                    assertThat(u.getFirstName()).isEqualTo("Integration");
                    assertThat(u.getLastName()).isEqualTo("Tester");
                });
    }
}
