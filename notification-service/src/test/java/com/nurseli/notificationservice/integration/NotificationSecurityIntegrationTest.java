package com.nurseli.notificationservice.integration;

import com.nurseli.notificationservice.integration.support.NotificationIntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationSecurityIntegrationTest extends NotificationIntegrationTestBase {

    @Test
    void protectedNotifications_withoutJwt_returns401Envelope() throws Exception {
        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.code").value("UNAUTHORIZED"));
    }

    @Test
    void unknownApiPath_returns401() throws Exception {
        mockMvc.perform(get("/api/other"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.code").value("UNAUTHORIZED"));
    }
}
