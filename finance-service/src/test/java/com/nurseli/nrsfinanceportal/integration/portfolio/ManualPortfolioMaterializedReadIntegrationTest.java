package com.nurseli.nrsfinanceportal.integration.portfolio;

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
import com.nurseli.nrsfinanceportal.integration.support.IntegrationTestJson;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManualPortfolioMaterializedReadIntegrationTest extends FinanceIntegrationTestBase {

    @Autowired
    private ManualPortfolioWarmupService warmupService;

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
    void pageBundle_afterWarmup_servesMaterializedRead() throws Exception {
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

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        warmupService.warmUser(testUser.getId());

        assertThat(readSnapshotRepository.findById(testUser.getId()))
                .isPresent()
                .get()
                .extracting(s -> s.getWarmStatus())
                .isEqualTo(ManualPortfolioWarmStatus.READY);

        mockMvc.perform(get("/api/portfolio/manual/page/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.positions[?(@.symbol == 'THYAO')]").exists())
                .andExpect(jsonPath("$.data.summary.totalPositions").value(1))
                .andExpect(jsonPath("$.data.timeseries.range").value("1Y"));
    }

    @Test
    void pageBundle_pending_returnsViewsWithoutBlockingTimeseries() throws Exception {
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

        long started = System.nanoTime();
        mockMvc.perform(get("/api/portfolio/manual/page/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meta.warmStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.timeseries.points").isEmpty())
                .andExpect(jsonPath("$.data.positions[?(@.symbol == 'THYAO')]").exists())
                .andExpect(jsonPath("$.data.summary.totalPositions").value(1));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertThat(elapsedMs).isLessThan(5_000L);
    }

    @Test
    void timeseries_pending_returnsEmptyWithoutSyncBuild() throws Exception {
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

        long started = System.nanoTime();
        mockMvc.perform(get("/api/portfolio/manual/timeseries/me")
                        .param("from", "2024-06-01")
                        .param("to", java.time.LocalDate.now().toString())
                        .with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertThat(elapsedMs).isLessThan(5_000L);
    }

    @Test
    void warmupPhaseA_servesKpiBeforeTimeseriesReady() throws Exception {
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

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        warmupService.warmUser(testUser.getId());

        assertThat(readSnapshotRepository.findById(testUser.getId()))
                .isPresent()
                .get()
                .extracting(s -> s.getWarmStatus())
                .isEqualTo(ManualPortfolioWarmStatus.READY);

        mockMvc.perform(get("/api/portfolio/manual/page/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meta.warmStatus").value("READY"))
                .andExpect(jsonPath("$.data.summary.totalPositions").value(1))
                .andExpect(jsonPath("$.data.positions[?(@.symbol == 'THYAO')]").exists());
    }

    @Test
    void pageBundle_afterDelete_doesNotServeStaleSnapshotPositions() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/portfolio/manual")
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
                .andExpect(status().isOk())
                .andReturn();

        long id = IntegrationTestJson.readLongId(created.getResponse().getContentAsString(), "$.data.id");

        commitOpenTestTransaction();
        awaitMaterializedBackgroundWork();

        warmupService.warmUser(testUser.getId());

        assertThat(readSnapshotRepository.findById(testUser.getId()))
                .isPresent()
                .get()
                .extracting(s -> s.getWarmStatus())
                .isEqualTo(ManualPortfolioWarmStatus.READY);

        mockMvc.perform(delete("/api/portfolio/manual/{id}", id).with(integrationUserJwt()))
                .andExpect(status().isOk());

        assertThat(positionRepository.findById(id)).isEmpty();

        commitOpenTestTransaction();
        awaitMaterializedBackgroundWork();
        entityManager.clear();

        mockMvc.perform(get("/api/portfolio/manual/page/me").with(integrationUserJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.meta.warmStatus").value("READY"))
                .andExpect(jsonPath("$.data.summary.totalPositions").value(0))
                .andExpect(jsonPath("$.data.positions[?(@.id == " + id + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.positions[?(@.symbol == 'THYAO')]").doesNotExist());
    }
}
