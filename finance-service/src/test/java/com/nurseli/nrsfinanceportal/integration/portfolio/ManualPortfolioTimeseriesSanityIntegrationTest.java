package com.nurseli.nrsfinanceportal.integration.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView;
import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioMaterializedStore;
import com.nurseli.nrsfinanceportal.application.portfolio.materialized.ManualPortfolioWarmupService;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioWarmStatus;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioPositionRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioReadSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.ManualPortfolioTimeseriesSnapshotRepository;
import com.nurseli.nrsfinanceportal.integration.support.FinanceIntegrationTestBase;
import com.nurseli.nrsfinanceportal.integration.support.IntegrationTestMarketStubs;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManualPortfolioTimeseriesSanityIntegrationTest extends FinanceIntegrationTestBase {

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
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanMaterializedState() {
        IntegrationTestMarketStubs.stubBistPortfolioWarmupSymbols(marketDataClient);
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
    void deleteAllThenSinglePosition_timeseriesMatchesKpiAndStartsAtBuyDate() throws Exception {
        LocalDate buyDate = LocalDate.of(2024, 6, 1);

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

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        warmupService.warmUser(testUser.getId());
        assertThat(timeseriesSnapshotRepository.findByUserId(testUser.getId())).isNotEmpty();

        var positions = positionRepository.findByUserIdOrderByBuyDateAsc(testUser.getId());
        assertThat(positions).hasSize(2);
        for (var p : positions) {
            mockMvc.perform(delete("/api/portfolio/manual/{id}", p.getId()).with(integrationUserJwt()))
                    .andExpect(status().isOk());
        }

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        warmupService.warmUser(testUser.getId());
        assertThat(timeseriesSnapshotRepository.findByUserId(testUser.getId())).isEmpty();

        mockMvc.perform(post("/api/portfolio/manual")
                        .with(integrationUserJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {
                                  "type": "BIST",
                                  "symbol": "THYAO",
                                  "quantity": 10,
                                  "buyPrice": 285.50,
                                  "buyPriceOverride": true,
                                  "buyFee": 0,
                                  "buyDate": "%s"
                                }
                                """, buyDate)))
                .andExpect(status().isOk());

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        warmupService.warmUser(testUser.getId());

        var snapshot = readSnapshotRepository.findById(testUser.getId()).orElseThrow();
        assertThat(snapshot.getWarmStatus()).isEqualTo(ManualPortfolioWarmStatus.READY);

        var tsOpt = materializedStore.findTimeseries(
                testUser.getId(),
                ManualPortfolioMaterializedStore.SERIES_VALUE_1Y,
                snapshot.getPositionsFingerprint()
        );
        assertThat(tsOpt).isPresent();
        List<ManualPortfolioTimeseriesPointDto> ts = tsOpt.get();
        assertThat(ts).isNotEmpty();

        ManualPortfolioSummaryView summary = materializedStore
                .readPayload(snapshot)
                .summary();
        assertThat(summary.getCurrentOpenValue()).isNotNull();
        assertThat(summary.getCurrentOpenValue().signum()).isGreaterThan(0);

        BigDecimal lastMarket = ts.stream()
                .filter(p -> p.marketValueTry() != null && p.marketValueTry().signum() > 0)
                .max(Comparator.comparing(ManualPortfolioTimeseriesPointDto::date))
                .map(ManualPortfolioTimeseriesPointDto::marketValueTry)
                .orElseThrow();

        BigDecimal ratio = lastMarket.divide(summary.getCurrentOpenValue(), 8, RoundingMode.HALF_UP);
        assertThat(ratio.doubleValue()).isBetween(0.85, 1.15);

        LocalDate firstDate = ts.stream()
                .map(ManualPortfolioTimeseriesPointDto::date)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElseThrow();
        assertThat(firstDate).isAfterOrEqualTo(buyDate);
    }
}
