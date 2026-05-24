package com.nurseli.notificationservice.integration;

import com.nurseli.notificationservice.integration.support.NotificationIntegrationFixtures;
import com.nurseli.notificationservice.integration.support.NotificationIntegrationTestBase;
import com.nurseli.notificationservice.infrastructure.persistence.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationEventFlowIntegrationTest extends NotificationIntegrationTestBase {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void kafkaNotificationEvent_createsInAppNotification() {
        long before = notificationRepository.count();

        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(
                        NotificationIntegrationFixtures.TEST_USER_SUB,
                        "Kafka created alert"));

        awaitUntil(Duration.ofSeconds(20), () -> {
            long after = notificationRepository.count();
            assertThat(after).isEqualTo(before + 1);
        });

        var page = notificationRepository.findByUserSubOrderByCreatedAtDesc(
                NotificationIntegrationFixtures.TEST_USER_SUB,
                PageRequest.of(0, 5));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getTitle()).isEqualTo("Kafka created alert");
        assertThat(page.getContent().getFirst().getType()).isEqualTo("REAL_RETURN_NEGATIVE");
    }

    @Test
    void kafkaDuplicateInAppEvent_incrementsOccurrenceCount() {
        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(
                        NotificationIntegrationFixtures.TEST_USER_SUB,
                        "Dedup alert v1"));
        awaitUntil(Duration.ofSeconds(20), () ->
                assertThat(notificationRepository.count()).isEqualTo(1));

        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(
                        NotificationIntegrationFixtures.TEST_USER_SUB,
                        "Dedup alert v2"));

        awaitUntil(Duration.ofSeconds(20), () -> {
            var notification = notificationRepository
                    .findFirstByUserSubAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
                            NotificationIntegrationFixtures.TEST_USER_SUB,
                            "REAL_RETURN_NEGATIVE")
                    .orElseThrow();
            assertThat(notification.getOccurrenceCount()).isEqualTo(2);
            assertThat(notification.getTitle()).isEqualTo("Dedup alert v2");
        });

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void createdKafkaNotification_isVisibleViaApi() throws Exception {
        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(
                        NotificationIntegrationFixtures.TEST_USER_SUB,
                        "Visible via API"));

        awaitUntil(Duration.ofSeconds(20), () ->
                assertThat(notificationRepository.count()).isEqualTo(1));

        mockMvc.perform(get("/api/notifications/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.content[0].title")
                        .value("Visible via API"));
    }
}
