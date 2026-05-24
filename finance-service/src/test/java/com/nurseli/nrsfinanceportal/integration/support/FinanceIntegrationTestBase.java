package com.nurseli.nrsfinanceportal.integration.support;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserEnablementClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserProfileClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers(disabledWithoutDocker = true)
@Import(IntegrationTestJwtConfig.class)
@Transactional
public abstract class FinanceIntegrationTestBase {

    public static final String TEST_KEYCLOAK_SUB = "integration-test-user-sub";
    public static final String TEST_EMAIL = "integration-test@nrs.local";
    public static final String TEST_USERNAME = "integration_test_user";

    public static final String TEST_ADMIN_SUB = "integration-test-admin-sub";
    public static final String TEST_ADMIN_EMAIL = "integration-admin@nrs.local";
    public static final String TEST_ADMIN_USERNAME = "integration_admin";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nrs_finance_it")
            .withUsername("nrs")
            .withPassword("nrs123");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

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
    void setUpIntegrationContext() {
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
    }

    protected RequestPostProcessor integrationUserJwt() {
        return jwtFor(TEST_KEYCLOAK_SUB, TEST_EMAIL, TEST_USERNAME, List.of("USER"));
    }

    protected RequestPostProcessor integrationAdminJwt() {
        return jwtFor(TEST_ADMIN_SUB, TEST_ADMIN_EMAIL, TEST_ADMIN_USERNAME, List.of("ADMIN", "USER"));
    }

    private static RequestPostProcessor jwtFor(String sub, String email, String username, List<String> roles) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token
                .subject(sub)
                .claim("email", email)
                .claim("preferred_username", username)
                .claim("email_verified", true)
                .claim("realm_access", Map.of("roles", roles)));
    }
}
