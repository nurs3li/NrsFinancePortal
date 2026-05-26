package com.nurseli.marketdata.application.bootstrap;

import com.nurseli.marketdata.api.dto.DebtHistoryCoverageResponse;
import com.nurseli.marketdata.application.DebtHistoryWarmupService;
import com.nurseli.marketdata.application.deposit.DepositRatesIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondEvdsIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondInstrumentIngestService;
import com.nurseli.marketdata.application.inflation.InflationIndexIngestService;
import com.nurseli.marketdata.application.loan.LoanRatesMacroService;
import com.nurseli.marketdata.application.MetalHistoryWarmupService;
import com.nurseli.marketdata.config.DebtHistoryBackfillProperties;
import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import com.nurseli.marketdata.config.InflationBackfillProperties;
import com.nurseli.marketdata.config.LoanRatesProperties;
import com.nurseli.marketdata.config.MarketBootstrapProperties;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * İlk kurulum: config {@code from} → bugün. Dolu DB: max(tarih)+1 → bugün. EVDS fazları sıralı + throttle.
 */
@Component
@ConditionalOnProperty(prefix = "market.bootstrap", name = "orchestrate-startup", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MarketBootstrapOrchestrator {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private final MarketBootstrapProperties bootstrapProperties;
    private final BootstrapRangeResolver rangeResolver;
    private final BootstrapWatermarkQuery watermarks;
    private final InflationBackfillProperties inflationBackfillProperties;
    private final InflationIndexIngestService inflationIndexIngestService;
    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesIngestService depositRatesIngestService;
    private final LoanRatesProperties loanRatesProperties;
    private final LoanRatesMacroService loanRatesMacroService;
    private final EurobondEvdsProperties eurobondEvdsProperties;
    private final EurobondEvdsIngestService eurobondEvdsIngestService;
    private final EurobondInstrumentIngestService eurobondInstrumentIngestService;
    private final EvdsProperties evdsProperties;
    private final DebtHistoryBackfillProperties debtHistoryBackfillProperties;
    private final DebtHistoryWarmupService debtHistoryWarmupService;
    private final MarketMetalsIsyatirimProperties metalsProperties;
    private final MetalHistoryWarmupService metalHistoryWarmupService;
    private final AtomicBoolean started = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleBootstrap() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Thread worker =
                new Thread(this::runPhases, "market-bootstrap-orchestrator");
        worker.setDaemon(true);
        worker.start();
        log.info("[BOOTSTRAP] orchestrator scheduled initialDelayMs={}", bootstrapProperties.getInitialDelayMs());
    }

    private void runPhases() {
        sleep(bootstrapProperties.getInitialDelayMs(), "initial");
        runInflation();
        sleep(bootstrapProperties.getPhaseDelayMs(), "after_inflation");
        runDepositRates();
        sleep(bootstrapProperties.getPhaseDelayMs(), "after_deposit");
        runLoanRates();
        sleep(bootstrapProperties.getPhaseDelayMs(), "after_loan_rates");
        runEurobond();
        sleep(bootstrapProperties.getPhaseDelayMs(), "after_eurobond");
        runDebt();
        sleep(bootstrapProperties.getPhaseDelayMs(), "after_debt");
        runMetals();
        log.info("[BOOTSTRAP] orchestrator finished");
    }

    private void runInflation() {
        if (!inflationBackfillProperties.isEnabled() || !inflationBackfillProperties.isStartupEnabled()) {
            log.info("[BOOTSTRAP] inflation skipped (backfill disabled)");
            return;
        }
        LocalDate to = LocalDate.now(IST);
        LocalDate configFrom = inflationBackfillProperties.getFrom();
        ResolvedBootstrapRange range =
                rangeResolver.resolveMonthly(configFrom, to, watermarks.inflationMaxObservationMonth());
        log.info("[BOOTSTRAP] inflation mode={} from={} to={} reason={}", range.mode(), range.from(), range.to(), range.reason());
        if (!range.shouldFetch()) {
            return;
        }
        try {
            inflationIndexIngestService.backfill(range.from(), range.to());
        } catch (Exception ex) {
            log.warn("[BOOTSTRAP] inflation failed {}: {}", ex.getClass().getSimpleName(), bootstrapFailureMessage(ex));
        }
    }

    private void runDepositRates() {
        if (!depositRatesProperties.isEnabled() || !depositRatesProperties.getBackfill().isEnabled()) {
            log.info("[BOOTSTRAP] deposit-rates skipped");
            return;
        }
        LocalDate to = LocalDate.now(IST);
        LocalDate configFrom = depositRatesProperties.getBackfill().getFrom();
        ResolvedBootstrapRange range =
                rangeResolver.resolveDaily(configFrom, to, watermarks.depositMaxObservationDate());
        log.info("[BOOTSTRAP] deposit-rates mode={} from={} to={} reason={}", range.mode(), range.from(), range.to(), range.reason());
        if (!range.shouldFetch()) {
            return;
        }
        try {
            depositRatesIngestService.ingestRange(range.from(), range.to());
        } catch (Exception ex) {
            log.warn("[BOOTSTRAP] deposit-rates failed {}: {}", ex.getClass().getSimpleName(), bootstrapFailureMessage(ex));
        }
    }

    private void runLoanRates() {
        if (!loanRatesProperties.isEnabled() || !loanRatesProperties.getBackfill().isEnabled()) {
            log.info("[BOOTSTRAP] loan-rates skipped");
            return;
        }
        LocalDate to = LocalDate.now(IST);
        LocalDate configFrom = loanRatesProperties.getBackfill().getFrom();
        long rows = watermarks.loanRateRowCount();
        int threshold = Math.max(1, bootstrapProperties.getLoanRatesFullSeedRowThreshold());
        if (rows < threshold) {
            log.info("[BOOTSTRAP] loan-rates full_seed rows={} threshold={}", rows, threshold);
            try {
                loanRatesMacroService.ingestRange(configFrom, to);
            } catch (Exception ex) {
                log.warn("[BOOTSTRAP] loan-rates full_seed failed {}: {}", ex.getClass().getSimpleName(), bootstrapFailureMessage(ex));
            }
            return;
        }
        ResolvedBootstrapRange range =
                rangeResolver.resolveDaily(configFrom, to, watermarks.loanRateMaxObservationDate());
        log.info("[BOOTSTRAP] loan-rates mode={} from={} to={} reason={}", range.mode(), range.from(), range.to(), range.reason());
        if (!range.shouldFetch()) {
            return;
        }
        try {
            loanRatesMacroService.ingestRange(range.from(), range.to());
        } catch (Exception ex) {
            log.warn("[BOOTSTRAP] loan-rates failed {}: {}", ex.getClass().getSimpleName(), bootstrapFailureMessage(ex));
        }
    }

    private void runEurobond() {
        if (!eurobondEvdsProperties.isEnabled()
                || !eurobondEvdsProperties.getBackfill().isStartupEnabled()) {
            log.info("[BOOTSTRAP] eurobond skipped");
            return;
        }
        LocalDate to = LocalDate.now(IST);
        LocalDate configFrom = eurobondEvdsProperties.getBackfill().getFrom();
        long rows = watermarks.eurobondRowCount();
        int threshold = Math.max(1, bootstrapProperties.getEurobondFullSeedRowThreshold());
        if (rows < threshold) {
            log.info("[BOOTSTRAP] eurobond full_seed rows={} threshold={}", rows, threshold);
            try {
                eurobondEvdsIngestService.ingestRange(configFrom, to);
            } catch (Exception ex) {
                log.warn("[BOOTSTRAP] eurobond full_seed failed {}: {}", ex.getClass().getSimpleName(), bootstrapFailureMessage(ex));
            }
        } else {
            ResolvedBootstrapRange range =
                    rangeResolver.resolveDaily(configFrom, to, watermarks.eurobondMaxObservationDate());
            log.info(
                    "[BOOTSTRAP] eurobond mode={} from={} to={} reason={}",
                    range.mode(),
                    range.from(),
                    range.to(),
                    range.reason());
            if (range.shouldFetch()) {
                try {
                    eurobondEvdsIngestService.ingestRange(range.from(), range.to());
                } catch (Exception ex) {
                    log.warn("[BOOTSTRAP] eurobond gap failed {}: {}", ex.getClass().getSimpleName(), bootstrapFailureMessage(ex));
                }
            } else {
                int weeks = Math.max(1, bootstrapProperties.getEurobondRecentWeeksWhenSeeded());
                eurobondEvdsIngestService.ingestRecentWeeks(weeks);
                log.info("[BOOTSTRAP] eurobond recent_weeks={} (up_to_date)", weeks);
            }
        }
        if (eurobondEvdsProperties.getInstruments().isStartupBackfillEnabled()) {
            ResolvedBootstrapRange insRange =
                    rangeResolver.resolveDaily(configFrom, to, watermarks.eurobondMaxObservationDate());
            if (insRange.shouldFetch()) {
                try {
                    eurobondInstrumentIngestService.ingestHistory(insRange.from(), insRange.to());
                } catch (Exception ex) {
                    log.warn("[BOOTSTRAP] eurobond instruments failed {}: {}", ex.getClass().getSimpleName(), bootstrapFailureMessage(ex));
                }
            }
        }
    }

    private void runDebt() {
        if (!evdsProperties.isEnabled() || evdsProperties.getDebt() == null || !evdsProperties.getDebt().isEnabled()) {
            log.info("[BOOTSTRAP] debt skipped");
            return;
        }
        if (evdsProperties.getDebt().getInstruments() == null || evdsProperties.getDebt().getInstruments().isEmpty()) {
            log.info("[BOOTSTRAP] debt skipped (no instruments)");
            return;
        }
        int periodDays = Math.max(30, debtHistoryBackfillProperties.getPeriodDays() > 0
                ? debtHistoryBackfillProperties.getPeriodDays()
                : 730);
        LocalDate to = LocalDate.now(IST);
        LocalDate configFrom = to.minusDays(periodDays - 1L);
        for (EvdsProperties.Instrument instrument : evdsProperties.getDebt().getInstruments()) {
            runDebtWarmup(instrument, configFrom, to);
        }
    }

    private void runDebtWarmup(EvdsProperties.Instrument instrument, LocalDate configFrom, LocalDate to) {
        if (instrument == null || instrument.getIsin() == null || instrument.getIsin().isBlank()) {
            return;
        }
        String isin = instrument.getIsin().trim().toUpperCase();
        DebtHistoryCoverageResponse coverage = debtHistoryWarmupService.getCoverage(isin, configFrom, to);
        if (coverage.isin() == null || coverage.requestedFrom() == null || coverage.requestedTo() == null) {
            log.info("[BOOTSTRAP] debt {} skipped invalid_range from={} to={}", isin, configFrom, to);
            return;
        }
        if (coverage.oldestAvailable() == null || coverage.oldestAvailable().isAfter(coverage.requestedFrom())) {
            log.info(
                    "[BOOTSTRAP] debt {} mode=FULL_SEED from={} to={} oldest={} newest={} availableDays={} expectedDays={} reason=depth_insufficient",
                    coverage.isin(),
                    coverage.requestedFrom(),
                    coverage.requestedTo(),
                    coverage.oldestAvailable(),
                    coverage.newestAvailable(),
                    coverage.availableDays(),
                    coverage.expectedDays());
            warmDebtRange(coverage.isin(), coverage.requestedFrom(), coverage.requestedTo());
            return;
        }
        if (coverage.availableDays() < coverage.expectedDays()) {
            log.info(
                    "[BOOTSTRAP] debt {} mode=FULL_SEED from={} to={} oldest={} newest={} availableDays={} expectedDays={} reason=coverage_gap",
                    coverage.isin(),
                    coverage.requestedFrom(),
                    coverage.requestedTo(),
                    coverage.oldestAvailable(),
                    coverage.newestAvailable(),
                    coverage.availableDays(),
                    coverage.expectedDays());
            warmDebtRange(coverage.isin(), coverage.requestedFrom(), coverage.requestedTo());
            return;
        }
        ResolvedBootstrapRange range =
                rangeResolver.resolveDaily(coverage.requestedFrom(), coverage.requestedTo(), watermarks.debtMaxObservationDate(isin));
        log.info(
                "[BOOTSTRAP] debt {} mode={} from={} to={} oldest={} newest={} availableDays={} expectedDays={} reason={}",
                coverage.isin(),
                range.mode(),
                range.from(),
                range.to(),
                coverage.oldestAvailable(),
                coverage.newestAvailable(),
                coverage.availableDays(),
                coverage.expectedDays(),
                range.reason());
        if (!range.shouldFetch()) {
            return;
        }
        warmDebtRange(coverage.isin(), range.from(), range.to());
    }

    private void warmDebtRange(String isin, LocalDate from, LocalDate to) {
        try {
            debtHistoryWarmupService.warmupSync(isin, from, to, "bootstrap");
        } catch (Exception ex) {
            log.warn(
                    "[BOOTSTRAP] debt {} failed {}: {}",
                    isin,
                    ex.getClass().getSimpleName(),
                    bootstrapFailureMessage(ex));
        }
    }

    private void runMetals() {
        if (!bootstrapProperties.isMetalsStartupEnabled() || !metalsProperties.isEnabled()) {
            log.info("[BOOTSTRAP] metals skipped");
            return;
        }
        LocalDate to = LocalDate.now(IST);
        LocalDate configFrom = to.minusYears(Math.max(1, metalsProperties.getDefaultLookbackYears()));
        runMetalWarmup("XAU_TRY", configFrom, to);
        for (PreciousMetalUsdCatalog.Entry entry : PreciousMetalUsdCatalog.all()) {
            runMetalWarmup(entry.canonicalSymbol(), configFrom, to);
        }
    }

    private void runMetalWarmup(String symbol, LocalDate configFrom, LocalDate to) {
        MetalHistoryWarmupService.MetalHistoryCoverage coverage =
                metalHistoryWarmupService.getCoverage(symbol, configFrom, to);
        if (coverage.symbol() == null || coverage.from() == null || coverage.to() == null) {
            log.info("[BOOTSTRAP] metal {} skipped invalid_range from={} to={}", symbol, configFrom, to);
            return;
        }
        if (coverage.oldestDay() == null || coverage.oldestDay().isAfter(coverage.from())) {
            log.info(
                    "[BOOTSTRAP] metal {} mode=FULL_SEED from={} to={} oldest={} newest={} availableDays={} expectedDays={} reason=depth_insufficient",
                    coverage.symbol(),
                    coverage.from(),
                    coverage.to(),
                    coverage.oldestDay(),
                    coverage.newestDay(),
                    coverage.availableDays(),
                    coverage.expectedDays());
            warmMetalRange(coverage.symbol(), coverage.from(), coverage.to());
            return;
        }

        ResolvedBootstrapRange range =
                rangeResolver.resolveDaily(coverage.from(), coverage.to(), Optional.ofNullable(coverage.newestDay()));
        log.info(
                "[BOOTSTRAP] metal {} mode={} from={} to={} oldest={} newest={} availableDays={} expectedDays={} reason={}",
                coverage.symbol(),
                range.mode(),
                range.from(),
                range.to(),
                coverage.oldestDay(),
                coverage.newestDay(),
                coverage.availableDays(),
                coverage.expectedDays(),
                range.reason());
        if (!range.shouldFetch()) {
            return;
        }
        warmMetalRange(coverage.symbol(), range.from(), range.to());
    }

    private void warmMetalRange(String symbol, LocalDate from, LocalDate to) {
        try {
            metalHistoryWarmupService.warmupSymbolSync(symbol, from, to, "bootstrap");
        } catch (Exception ex) {
            log.warn(
                    "[BOOTSTRAP] metal {} failed {}: {}",
                    symbol,
                    ex.getClass().getSimpleName(),
                    bootstrapFailureMessage(ex));
        }
        sleep(bootstrapProperties.getMetalsDelayMs(), "metal_" + symbol);
    }

    private static String bootstrapFailureMessage(Throwable ex) {
        String msg = ex.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return ex.toString();
    }

    private void sleep(long ms, String label) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[BOOTSTRAP] sleep interrupted label={}", label);
        }
    }
}
