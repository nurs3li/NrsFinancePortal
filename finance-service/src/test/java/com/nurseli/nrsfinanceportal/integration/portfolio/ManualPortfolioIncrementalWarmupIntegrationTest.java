package com.nurseli.nrsfinanceportal.integration.portfolio;

import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioMaterializedStore;
import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioWarmupService;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioWarmStatus;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioReadSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioTimeseriesSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualSymbolDailyCloseRepository;
import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import com.nurseli.nrsfinanceportal.integration.support.IntegrationTestMarketStubs;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManualPortfolioIncrementalWarmupIntegrationTest extends FinanceIntegrationTestBase {

    @Autowired
    private ManualPortfolioWarmupService warmupService;

    @Autowired
    private ManualPortfolioMaterializedStore materializedStore;

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
    void cleanMaterializedState() throws InterruptedException {
        IntegrationTestMarketStubs.stubBistPortfolioWarmupSymbols(marketDataClient);
        TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
        cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> {
            timeseriesSnapshotRepository.deleteByUserId(testUser.getId());
            dailyCloseRepository.deleteByUserId(testUser.getId());
            readSnapshotRepository.deleteById(testUser.getId());
            positionRepository.deleteByUser_Id(testUser.getId());
        });
        entityManager.clear();
        awaitMaterializedBackgroundWork();
    }

    @Test
    void warmup_secondSymbol_doesNotReloadExistingSymbolHistory() throws Exception {
        mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "THYAO",
                                  "quantity": 10,
                                  "buyPrice": 285.50,
                                  "buyPriceOverride": true,
                                  "buyFee": 0,
                                  "buyDate": "2024-06-01"
                                }
                                """))
                .andExpect(status().isOk());

        commitOpenTestTransaction();
        awaitMaterializedBackgroundWork();

        warmupService.warmUser(testUser.getId());
        awaitMaterializedBackgroundWork();

        int thyaoRowsBefore = dailyCloseRepository.findRange(
                testUser.getId(),
                AssetType.BIST,
                "THYAO",
                LocalDate.of(2020, 1, 1),
                LocalDate.now().plusDays(1)
        ).size();
        assertThat(thyaoRowsBefore).isGreaterThan(0);

        mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "BIST",
                                  "symbol": "GARAN",
                                  "quantity": 5,
                                  "buyPrice": 95.00,
                                  "buyPriceOverride": true,
                                  "buyFee": 0,
                                  "buyDate": "2023-11-30"
                                }
                                """))
                .andExpect(status().isOk());

        commitOpenTestTransaction();
        awaitMaterializedBackgroundWork();

        warmupService.warmUser(testUser.getId());
        awaitMaterializedBackgroundWork();

        var snapshot = readSnapshotRepository.findById(testUser.getId()).orElseThrow();
        assertThat(snapshot.getWarmStatus()).isEqualTo(ManualPortfolioWarmStatus.READY);
        var tsAfterSecond = materializedStore.findTimeseries(
                testUser.getId(),
                ManualPortfolioMaterializedStore.SERIES_VALUE_1Y,
                snapshot.getPositionsFingerprint()
        );
        assertThat(tsAfterSecond).isPresent();
        assertThat(tsAfterSecond.get()).isNotEmpty();

        int thyaoRowsAfter = dailyCloseRepository.findRange(
                testUser.getId(),
                AssetType.BIST,
                "THYAO",
                LocalDate.of(2020, 1, 1),
                LocalDate.now().plusDays(1)
        ).size();
        int garanRows = dailyCloseRepository.findRange(
                testUser.getId(),
                AssetType.BIST,
                "GARAN",
                LocalDate.of(2020, 1, 1),
                LocalDate.now().plusDays(1)
        ).size();

        assertThat(garanRows).isGreaterThan(0);
        assertThat(thyaoRowsAfter - thyaoRowsBefore).isLessThanOrEqualTo(3);
    }
}
