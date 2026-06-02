package com.nurseli.marketdata.application.bootstrap;

import com.nurseli.marketdata.api.dto.DebtHistoryCoverageResponse;
import com.nurseli.marketdata.application.ingest.DebtHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.MetalHistoryWarmupService;
import com.nurseli.marketdata.application.deposit.DepositRatesIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondEvdsIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondInstrumentIngestService;
import com.nurseli.marketdata.application.inflation.InflationIndexIngestService;
import com.nurseli.marketdata.application.loan.LoanRatesMacroService;
import com.nurseli.marketdata.config.DebtHistoryBackfillProperties;
import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import com.nurseli.marketdata.config.InflationBackfillProperties;
import com.nurseli.marketdata.config.LoanRatesProperties;
import com.nurseli.marketdata.config.MarketBootstrapProperties;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketBootstrapOrchestratorDebtTest {

    @Mock
    private BootstrapWatermarkQuery watermarks;
    @Mock
    private InflationIndexIngestService inflationIndexIngestService;
    @Mock
    private DepositRatesIngestService depositRatesIngestService;
    @Mock
    private LoanRatesMacroService loanRatesMacroService;
    @Mock
    private EurobondEvdsIngestService eurobondEvdsIngestService;
    @Mock
    private EurobondInstrumentIngestService eurobondInstrumentIngestService;
    @Mock
    private DebtHistoryWarmupService debtHistoryWarmupService;
    @Mock
    private MetalHistoryWarmupService metalHistoryWarmupService;

    @Test
    void scheduleBootstrap_triggersDebtWarmupWhenCoverageDepthMissing() {
        LocalDate today = LocalDate.now();

        MarketBootstrapProperties bootstrapProperties = new MarketBootstrapProperties();
        bootstrapProperties.setInitialDelayMs(0);
        bootstrapProperties.setPhaseDelayMs(0);
        bootstrapProperties.setMetalsStartupEnabled(false);

        InflationBackfillProperties inflationProps = new InflationBackfillProperties();
        inflationProps.setEnabled(false);

        DepositRatesProperties depositProps = new DepositRatesProperties();
        depositProps.setEnabled(false);

        LoanRatesProperties loanProps = new LoanRatesProperties();
        loanProps.setEnabled(false);

        EurobondEvdsProperties eurobondProps = new EurobondEvdsProperties();
        eurobondProps.setEnabled(false);

        EvdsProperties evdsProperties = new EvdsProperties();
        evdsProperties.setEnabled(true);
        evdsProperties.getDebt().setEnabled(true);
        EvdsProperties.Instrument instrument = new EvdsProperties.Instrument();
        instrument.setIsin("TRT170227K64");
        evdsProperties.getDebt().setInstruments(List.of(instrument));

        DebtHistoryBackfillProperties debtProps = new DebtHistoryBackfillProperties();
        debtProps.setPeriodDays(30);

        MarketMetalsIsyatirimProperties metalsProps = new MarketMetalsIsyatirimProperties();
        metalsProps.setEnabled(false);

        when(debtHistoryWarmupService.getCoverage(eq("TRT170227K64"), eq(today.minusDays(29)), eq(today)))
                .thenReturn(new DebtHistoryCoverageResponse(
                        "TRT170227K64",
                        today.minusDays(29),
                        today,
                        null,
                        null,
                        0,
                        30,
                        false));

        MarketBootstrapOrchestrator orchestrator = new MarketBootstrapOrchestrator(
                bootstrapProperties,
                new BootstrapRangeResolver(),
                watermarks,
                inflationProps,
                inflationIndexIngestService,
                depositProps,
                depositRatesIngestService,
                loanProps,
                loanRatesMacroService,
                eurobondProps,
                eurobondEvdsIngestService,
                eurobondInstrumentIngestService,
                evdsProperties,
                debtProps,
                debtHistoryWarmupService,
                metalsProps,
                metalHistoryWarmupService);

        orchestrator.scheduleBootstrap();

        verify(debtHistoryWarmupService, timeout(1000))
                .warmupSync(eq("TRT170227K64"), eq(today.minusDays(29)), eq(today), eq("bootstrap"));
    }
}
