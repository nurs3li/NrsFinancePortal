package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.DebtIngestService;
import com.nurseli.marketdata.application.ViopIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ViopDebtMarketScheduler {

    private final ViopIngestService viopIngestService;
    private final DebtIngestService debtIngestService;
    @Value("${app.viop.live-ingest-enabled:false}")
    private boolean viopLiveIngestEnabled;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void ingestViopAndDebt() {
        if (viopLiveIngestEnabled) {
            try {
                viopIngestService.ingestLatest();
                log.info("[SCHEDULER] VIOP MVP snapshots ingested");
            } catch (Exception ex) {
                log.warn("[SCHEDULER] VIOP ingest failed: {}", ex.getMessage());
            }
        } else {
            log.debug("[SCHEDULER] VIOP live ingest disabled (static CSV/backfill mode)");
        }
        try {
            debtIngestService.ingestLatest();
            log.info("[SCHEDULER] Debt MVP snapshots ingested");
        } catch (Exception ex) {
            log.warn("[SCHEDULER] Debt ingest failed: {}", ex.getMessage());
        }
    }
}
