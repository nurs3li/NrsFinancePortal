package com.nurseli.nrsfinanceportal.integration.support;

import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioGapFillService;
import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioWarmupService;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserEnablementClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserProfileClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * finance-service integration test tabanı: Postgres (Testcontainers), Liquibase, MockMvc, test JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
@Import(IntegrationTestJwtConfig.class)
@Transactional
public abstract class FinanceIntegrationTestBase {

    static {
        FinanceIntegrationContainers.POSTGRES.getJdbcUrl();
    }

    public static final String TEST_KEYCLOAK_SUB = "integration-test-user-sub";
    public static final String TEST_EMAIL = "integration-test@nrs.local";
    public static final String TEST_USERNAME = "integration_test_user";

    public static final String TEST_ADMIN_SUB = "integration-test-admin-sub";
    public static final String TEST_ADMIN_EMAIL = "integration-admin@nrs.local";
    public static final String TEST_ADMIN_USERNAME = "integration_admin";

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", FinanceIntegrationContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", FinanceIntegrationContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", FinanceIntegrationContainers.POSTGRES::getPassword);
        registry.add("spring.data.redis.host", FinanceIntegrationContainers.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> FinanceIntegrationContainers.REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ManualPortfolioWarmupService manualPortfolioWarmupService;

    @Autowired
    private ManualPortfolioGapFillService manualPortfolioGapFillService;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    protected MarketDataClient marketDataClient;

    @MockitoBean
    protected KeycloakUserProfileClient keycloakUserProfileClient;

    @MockitoBean
    protected KeycloakUserEnablementClient keycloakUserEnablementClient;

    protected User testUser;
    protected User adminUser;

    @BeforeEach
    void setUpIntegrationContext() throws InterruptedException {
        awaitMaterializedBackgroundWork();
        IntegrationTestMarketStubs.stubPassiveMarketData(marketDataClient);
        doNothing().when(keycloakUserProfileClient).requireConfigured();
        doNothing().when(keycloakUserProfileClient).updateFullName(anyString(), any(), any());
        doNothing().when(keycloakUserProfileClient).updateUsername(anyString(), anyString());
        when(keycloakUserEnablementClient.isConfigured()).thenReturn(true);
        doNothing().when(keycloakUserEnablementClient).setEnabled(anyString(), anyBoolean());

        testUser = userRepository.findByKeycloakUserId(TEST_KEYCLOAK_SUB)
                .orElseGet(() -> userRepository.save(
                        User.createFromIdentity(TEST_KEYCLOAK_SUB, TEST_EMAIL, TEST_USERNAME, Role.USER)));

        adminUser = userRepository.findByKeycloakUserId(TEST_ADMIN_SUB)
                .orElseGet(() -> userRepository.save(
                        User.createFromIdentity(TEST_ADMIN_SUB, TEST_ADMIN_EMAIL, TEST_ADMIN_USERNAME, Role.ADMIN)));

        ensureLoginActive(testUser);
        ensureLoginActive(adminUser);
    }

    @AfterEach
    void resetIntegrationUserSuspensionState() throws InterruptedException {
        awaitMaterializedBackgroundWork();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            userRepository.findByKeycloakUserId(TEST_KEYCLOAK_SUB).ifPresent(this::ensureLoginActive);
            userRepository.findByKeycloakUserId(TEST_ADMIN_SUB).ifPresent(this::ensureLoginActive);
        });
    }

    private void ensureLoginActive(User user) {
        if (user.isLoginSuspended()) {
            user.unsuspendLogin();
            userRepository.saveAndFlush(user);
        }
    }

    protected void awaitMaterializedBackgroundWork() throws InterruptedException {
        manualPortfolioWarmupService.awaitIdle(Duration.ofSeconds(15));
        manualPortfolioGapFillService.awaitIdle(Duration.ofSeconds(5));
    }

    /** MockMvc POST/DELETE sonrası afterCommit ile tetiklenen async warmup'ın bitmesini bekler. */
    protected void commitOpenTestTransaction() {
        if (org.springframework.test.context.transaction.TestTransaction.isActive()) {
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();
            org.springframework.test.context.transaction.TestTransaction.start();
        }
    }

    protected RequestPostProcessor integrationUserJwt() {
        return jwtFor(TEST_KEYCLOAK_SUB, TEST_EMAIL, TEST_USERNAME, List.of("USER"));
    }

    protected RequestPostProcessor integrationAdminJwt() {
        return jwtFor(TEST_ADMIN_SUB, TEST_ADMIN_EMAIL, TEST_ADMIN_USERNAME, List.of("ADMIN", "USER"));
    }

    private static RequestPostProcessor jwtFor(String sub, String email, String username, List<String> roles) {
        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toArray(SimpleGrantedAuthority[]::new);

        return SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(authorities)
                .jwt(token -> token
                        .subject(sub)
                        .claim("email", email)
                        .claim("preferred_username", username)
                        .claim("email_verified", true)
                        .claim("realm_access", Map.of("roles", roles)));
    }
}
