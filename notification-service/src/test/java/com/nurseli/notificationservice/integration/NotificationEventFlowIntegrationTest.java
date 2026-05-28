package com.nurseli.notificationservice.integration;

import com.nurseli.notificationservice.integration.support.NotificationIntegrationFixtures;
import com.nurseli.notificationservice.integration.support.NotificationIntegrationTestBase;
import com.nurseli.notificationservice.infrastructure.persistence.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationEventFlowIntegrationTest extends NotificationIntegrationTestBase {

    private static final String TEST_USER = NotificationIntegrationFixtures.TEST_USER_SUB;

    @Autowired
    private NotificationRepository notificationRepository;

    private long userNotificationCount() {
        return notificationRepository.countByUserSubAndReadAtIsNull(TEST_USER);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void kafkaNotificationEvent_createsInAppNotification() {
        long before = userNotificationCount();

        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(TEST_USER, "Kafka created alert"));

        awaitUntil(Duration.ofSeconds(30), () ->
                assertThat(userNotificationCount()).isEqualTo(before + 1));

        var page = notificationRepository.findByUserSubOrderByCreatedAtDesc(
                TEST_USER,
                PageRequest.of(0, 5));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getTitle()).isEqualTo("Kafka created alert");
        assertThat(page.getContent().getFirst().getType()).isEqualTo("REAL_RETURN_NEGATIVE");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void kafkaDuplicateInAppEvent_incrementsOccurrenceCount() {
        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(TEST_USER, "Dedup alert v1"));
        awaitUntil(Duration.ofSeconds(30), () ->
                assertThat(userNotificationCount()).isEqualTo(1));

        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(TEST_USER, "Dedup alert v2"));

        awaitUntil(Duration.ofSeconds(30), () -> {
            var notification = notificationRepository
                    .findFirstByUserSubAndTypeAndReadAtIsNullOrderByCreatedAtDesc(
                            TEST_USER,
                            "REAL_RETURN_NEGATIVE")
                    .orElseThrow();
            assertThat(notification.getOccurrenceCount()).isEqualTo(2);
            assertThat(notification.getTitle()).isEqualTo("Dedup alert v2");
        });

        assertThat(userNotificationCount()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createdKafkaNotification_isVisibleViaApi() throws Exception {
        publishNotificationEvent(
                NotificationIntegrationFixtures.inAppOnlyEvent(TEST_USER, "Visible via API"));

        awaitUntil(Duration.ofSeconds(30), () ->
                assertThat(userNotificationCount()).isEqualTo(1));

        mockMvc.perform(get("/api/notifications/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Visible via API"));
    }
}
