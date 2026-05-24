package com.nurseli.notificationservice.integration;

import com.nurseli.notificationservice.integration.support.NotificationIntegrationFixtures;
import com.nurseli.notificationservice.integration.support.NotificationIntegrationTestBase;
import com.nurseli.notificationservice.infrastructure.persistence.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationQueryIntegrationTest extends NotificationIntegrationTestBase {

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void seedNotifications() {
        notificationRepository.save(
                NotificationIntegrationFixtures.unreadNotification(
                        NotificationIntegrationFixtures.TEST_USER_SUB,
                        "Portfolio alert",
                        "REAL_RETURN_NEGATIVE"));
        notificationRepository.save(
                NotificationIntegrationFixtures.unreadNotification(
                        NotificationIntegrationFixtures.OTHER_USER_SUB,
                        "Other user alert",
                        "PRICE_ALERT_IN_APP"));
    }

    @Test
    void getMyNotifications_returnsOnlyAuthenticatedUserRows() throws Exception {
        mockMvc.perform(get("/api/notifications/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Portfolio alert"))
                .andExpect(jsonPath("$.content[0].type").value("REAL_RETURN_NEGATIVE"));
    }

    @Test
    void getMyNotifications_unreadOnly_filtersReadRows() throws Exception {
        notificationRepository.save(
                NotificationIntegrationFixtures.unreadNotification(
                        NotificationIntegrationFixtures.TEST_USER_SUB,
                        "Read later",
                        "PRICE_ALERT_IN_APP"));

        mockMvc.perform(get("/api/notifications/me")
                        .param("unreadOnly", "true")
                        .with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getUnreadCount_returnsAuthenticatedUserCount() throws Exception {
        mockMvc.perform(get("/api/notifications/me/unread-count").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }
}
