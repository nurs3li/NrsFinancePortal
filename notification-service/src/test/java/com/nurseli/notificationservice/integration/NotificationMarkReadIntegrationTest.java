package com.nurseli.notificationservice.integration;

import com.nurseli.notificationservice.domain.Notification;
import com.nurseli.notificationservice.integration.support.NotificationIntegrationFixtures;
import com.nurseli.notificationservice.integration.support.NotificationIntegrationTestBase;
import com.nurseli.notificationservice.infrastructure.persistence.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationMarkReadIntegrationTest extends NotificationIntegrationTestBase {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void markAsRead_persistsReadAtForOwner() throws Exception {
        Notification saved = notificationRepository.save(
                NotificationIntegrationFixtures.unreadNotification(
                        NotificationIntegrationFixtures.TEST_USER_SUB,
                        "Mark me read",
                        "PRICE_ALERT_IN_APP"));

        mockMvc.perform(patch("/api/notifications/{id}/read", saved.getId()).with(integrationUserJwt()))
                .andExpect(status().isNoContent());

        Notification updated = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getReadAt()).isNotNull();
    }

    @Test
    void markAsRead_otherUsersNotification_returns404() throws Exception {
        Notification saved = notificationRepository.save(
                NotificationIntegrationFixtures.unreadNotification(
                        NotificationIntegrationFixtures.OTHER_USER_SUB,
                        "Not yours",
                        "PRICE_ALERT_IN_APP"));

        mockMvc.perform(patch("/api/notifications/{id}/read", saved.getId()).with(integrationUserJwt()))
                .andExpect(status().isNotFound());
    }
}
