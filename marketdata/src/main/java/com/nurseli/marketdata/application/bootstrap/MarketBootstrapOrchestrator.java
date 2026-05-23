package com.nurseli.marketdata.application.bootstrap;

import com.nurseli.marketdata.application.IsyatirimMetalUsdBackfillService;
import com.nurseli.marketdata.application.deposit.DepositRatesIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondEvdsIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondInstrumentIngestService;
import com.nurseli.marketdata.application.inflation.InflationIndexIngestService;
import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import com.nurseli.marketdata.config.InflationBackfillProperties;
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
    private final EurobondEvdsProperties eurobondEvdsProperties;
    private final EurobondEvdsIngestService eurobondEvdsIngestService;
    private final EurobondInstrumentIngestService eurobondInstrumentIngestService;
    private final MarketMetalsIsyatirimProperties metalsProperties;
    private final IsyatirimMetalUsdBackfillService isyatirimMetalUsdBackfillService;
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
        runEurobond();
        sleep(bootstrapProperties.getPhaseDelayMs(), "after_eurobond");
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

    private void runMetals() {
        if (!bootstrapProperties.isMetalsStartupEnabled() || !metalsProperties.isEnabled()) {
            log.info("[BOOTSTRAP] metals skipped");
            return;
        }
        LocalDate to = LocalDate.now(IST);
        for (PreciousMetalUsdCatalog.Entry entry : PreciousMetalUsdCatalog.all()) {
            LocalDate configFrom = to.minusYears(Math.max(1, metalsProperties.getDefaultLookbackYears()));
            ResolvedBootstrapRange range =
                    rangeResolver.resolveDaily(
                            configFrom, to, watermarks.metalMaxObservationDate(entry.canonicalSymbol()));
            log.info(
                    "[BOOTSTRAP] metal {} mode={} from={} to={} reason={}",
                    entry.canonicalSymbol(),
                    range.mode(),
                    range.from(),
                    range.to(),
                    range.reason());
            if (!range.shouldFetch()) {
                continue;
            }
            try {
                isyatirimMetalUsdBackfillService.run(entry.canonicalSymbol(), range.from(), range.to(), false);
            } catch (Exception ex) {
                log.warn(
                        "[BOOTSTRAP] metal {} failed {}: {}",
                        entry.canonicalSymbol(),
                        ex.getClass().getSimpleName(),
                        bootstrapFailureMessage(ex));
            }
            sleep(bootstrapProperties.getMetalsDelayMs(), "metal_" + entry.canonicalSymbol());
        }
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
