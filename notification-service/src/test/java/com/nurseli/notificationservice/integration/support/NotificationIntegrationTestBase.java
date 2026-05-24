package com.nurseli.notificationservice.integration.support;

import com.nurseli.notificationservice.application.event.NotificationRequestedEvent;
import com.nurseli.notificationservice.infrastructure.finance.FinanceUserClient;
import com.nurseli.notificationservice.infrastructure.gmail.GmailClient;
import com.nurseli.notificationservice.infrastructure.persistence.NotificationRepository;
import com.nurseli.notificationservice.infrastructure.security.S2SAccessTokenService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * notification-service integration test tabanı: Postgres + Redis (Testcontainers), EmbeddedKafka, MockMvc, test JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
@Import(IntegrationTestJwtConfig.class)
@Transactional
@EmbeddedKafka(partitions = 1, topics = NotificationIntegrationTestBase.NOTIFICATION_EVENTS_TOPIC)
public abstract class NotificationIntegrationTestBase {

    public static final String NOTIFICATION_EVENTS_TOPIC = "notification-events";

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", NotificationIntegrationContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", NotificationIntegrationContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", NotificationIntegrationContainers.POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("spring.data.redis.host", NotificationIntegrationContainers.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> NotificationIntegrationContainers.REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected KafkaTemplate<String, Object> notificationEventKafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @MockitoBean
    protected GmailClient gmailClient;

    @MockitoBean
    protected FinanceUserClient financeUserClient;

    @MockitoBean
    protected S2SAccessTokenService s2sAccessTokenService;

    @BeforeEach
    void stubExternalIntegrations() {
        doNothing().when(gmailClient).sendEmail(anyString(), anyString(), anyString());
        when(s2sAccessTokenService.getAccessToken()).thenReturn("integration-test-token");
    }

    @BeforeEach
    void cleanCommittedNotifications() {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> notificationRepository.deleteAll());
    }

    @BeforeEach
    void awaitKafkaConsumerAssignment() {
        kafkaListenerEndpointRegistry.getListenerContainers().forEach(container ->
                ContainerTestUtils.waitForAssignment(container, 1));
    }

    protected RequestPostProcessor integrationUserJwt() {
        return jwtFor(NotificationIntegrationFixtures.TEST_USER_SUB);
    }

    protected RequestPostProcessor integrationOtherUserJwt() {
        return jwtFor(NotificationIntegrationFixtures.OTHER_USER_SUB);
    }

    protected void publishNotificationEvent(NotificationRequestedEvent event) {
        try {
            notificationEventKafkaTemplate.send(NOTIFICATION_EVENTS_TOPIC, event).get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish notification event", e);
        }
    }

    protected void awaitUntil(Duration timeout, Executable assertion) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable last = null;

        while (System.nanoTime() < deadline) {
            try {
                entityManager.clear();
                assertDoesNotThrow(assertion);
                return;
            } catch (Throwable t) {
                last = t;
                sleepQuietly(250);
            }
        }

        if (last instanceof AssertionError ae) {
            throw ae;
        }
        fail("Condition not met within " + timeout, last);
    }

    private static RequestPostProcessor jwtFor(String sub) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(sub));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for async condition");
        }
    }
}
