package com.nurseli.marketdata.integration.support;

import com.nurseli.marketdata.api.dto.inflation.InflationBackfillResponse;
import com.nurseli.marketdata.application.EvdsCpiTrService;
import com.nurseli.marketdata.application.inflation.InflationIndexIngestService;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * marketdata integration test tabanı: Postgres + Redis (Testcontainers), Liquibase, MockMvc, test JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integrationtest")
@Import(IntegrationTestJwtConfig.class)
@Transactional
public abstract class MarketdataIntegrationTestBase {

    @DynamicPropertySource
    static void registerContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MarketdataIntegrationContainers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", MarketdataIntegrationContainers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", MarketdataIntegrationContainers.POSTGRES::getPassword);
        registry.add("spring.data.redis.host", MarketdataIntegrationContainers.REDIS::getHost);
        registry.add("spring.data.redis.port", () -> MarketdataIntegrationContainers.REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected EvdsDebtClient evdsDebtClient;

    @MockitoBean
    protected EvdsCpiTrService evdsCpiTrService;

    @MockitoBean
    protected InflationIndexIngestService inflationIndexIngestService;

    @BeforeEach
    void stubExternalMarketProviders() {
        when(evdsDebtClient.fetchSeriesAscending(anyString(), any(), any())).thenReturn(List.of());
        when(inflationIndexIngestService.backfill(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(new InflationBackfillResponse("ok", LocalDate.of(2024, 1, 1), LocalDate.now(), List.of()));
    }

    protected RequestPostProcessor integrationAdminJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(token -> token
                        .subject("integration-admin-sub")
                        .claim("email", "integration-admin@nrs.local")
                        .claim("preferred_username", "integration_admin")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN", "USER"))));
    }

    protected RequestPostProcessor integrationUserJwt() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .jwt(token -> token
                        .subject("integration-user-sub")
                        .claim("email", "integration-user@nrs.local")
                        .claim("preferred_username", "integration_user")
                        .claim("realm_access", Map.of("roles", List.of("USER"))));
    }
}
