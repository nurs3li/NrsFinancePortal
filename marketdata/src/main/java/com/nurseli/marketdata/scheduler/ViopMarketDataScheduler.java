package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.ViopMarketDataService;
import com.nurseli.marketdata.config.MarketViopProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.market.viop.scheduler", name = "enabled", havingValue = "true")
public class ViopMarketDataScheduler {

    private final MarketViopProperties viopProperties;
    private final ViopMarketDataService viopMarketDataService;

    @PostConstruct
    void logViopSchedulerConfig() {
        MarketViopProperties.Scheduler s = viopProperties.getScheduler();
        log.info(
                "VIOP_SCHEDULER_CONFIG masterEnabled={} viopIntegrationEnabled={} snapshotEnabled={} historyEnabled={} "
                        + "snapshotFixedDelayMs={} snapshotInitialDelayMs={} historyFixedDelayMs={} historyInitialDelayMs={} "
                        + "historyLookbackDays={} historyChunkDays={} "
                        + "periodMinutes={} defaultPeriodMinutes={} provider={} "
                        + "note=snapshot rows upsert by (contract_code,update_date,source); new provider timestamps append rows; "
                        + "history uses insert-on-conflict-do-nothing (see VIOP_HISTORY_UPSERT_SUCCESS skippedCount)",
                s.isEnabled(),
                viopProperties.isEnabled(),
                s.isSnapshotEnabled(),
                s.isHistoryEnabled(),
                s.getSnapshotFixedDelayMs(),
                s.getSnapshotInitialDelayMs(),
                s.getHistoryFixedDelayMs(),
                s.getHistoryInitialDelayMs(),
                s.getHistoryLookbackDays(),
                s.getHistoryChunkDays(),
                s.getPeriodMinutes() > 0 ? s.getPeriodMinutes() : viopProperties.getDefaultPeriodMinutes(),
                viopProperties.getDefaultPeriodMinutes(),
                viopProperties.getProvider());
    }

    @Scheduled(
            fixedDelayString = "${app.market.viop.scheduler.snapshot-fixed-delay-ms:3600000}",
            initialDelayString = "${app.market.viop.scheduler.snapshot-initial-delay-ms:120000}")
    public void refreshSnapshots() {
        if (!viopProperties.isEnabled() || !viopProperties.getScheduler().isSnapshotEnabled()) {
            return;
        }
        try {
            viopMarketDataService.refreshAllSnapshotsWithSchedulerLogging();
        } catch (Exception ex) {
            log.warn("VIOP_SNAPSHOT_SCHEDULER_RUN_FAILED error={}", ex.getMessage(), ex);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.market.viop.scheduler.history-fixed-delay-ms:3600000}",
            initialDelayString = "${app.market.viop.scheduler.history-initial-delay-ms:1980000}")
    public void refreshHistory() {
        if (!viopProperties.isEnabled() || !viopProperties.getScheduler().isHistoryEnabled()) {
            return;
        }
        try {
            viopMarketDataService.refreshRecentHistoryForAllContractsWithSchedulerLogging();
        } catch (Exception ex) {
            log.warn("VIOP_HISTORY_SCHEDULER_RUN_FAILED error={}", ex.getMessage(), ex);
        }
    }
}
