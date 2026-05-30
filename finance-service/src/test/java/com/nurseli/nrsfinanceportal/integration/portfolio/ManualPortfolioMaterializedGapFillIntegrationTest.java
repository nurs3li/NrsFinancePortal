package com.nurseli.nrsfinanceportal.integration.portfolio;

import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioWarmupService;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioReadSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioTimeseriesSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualSymbolDailyCloseRepository;
import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManualPortfolioMaterializedGapFillIntegrationTest extends FinanceIntegrationTestBase {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    @Autowired
    private ManualPortfolioWarmupService warmupService;

    @Autowired
    private ManualPortfolioReadSnapshotRepository readSnapshotRepository;

    @Autowired
    private ManualPortfolioTimeseriesSnapshotRepository timeseriesSnapshotRepository;

    @Autowired
    private ManualPortfolioPositionRepository positionRepository;

    @Autowired
    private ManualSymbolDailyCloseRepository dailyCloseRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanMaterializedState() {
        TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
        cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> {
            timeseriesSnapshotRepository.deleteByUserId(testUser.getId());
            readSnapshotRepository.deleteById(testUser.getId());
            positionRepository.deleteByUser_Id(testUser.getId());
        });
        entityManager.clear();
    }

    @Test
    void pageBundle_gapFill_triggersLimitedMdsWhenClosesStale() throws Exception {
        mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "ASELS",
                                  "quantity": 5,
                                  "buyPrice": 50.00,
                                  "buyPriceOverride": true,
                                  "buyDate": "2024-01-15"
                                }
                                """))
                .andExpect(status().isOk());

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        warmupService.warmUser(testUser.getId());

        LocalDate staleDate = LocalDate.now(TZ).minusDays(3);
        dailyCloseRepository.upsertNative(
                testUser.getId(),
                AssetType.BIST.name(),
                "ASELS",
                staleDate,
                new BigDecimal("52.00"),
                "TEST",
                Instant.now()
        );

        clearInvocations(marketDataClient);

        mockMvc.perform(get("/api/portfolio/manual/page/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.gapFillMs").isNumber());

        verify(marketDataClient, atMost(2)).loadLatestPricing();
    }
}
