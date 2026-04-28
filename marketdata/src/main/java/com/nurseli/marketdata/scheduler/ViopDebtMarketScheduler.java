package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.DebtIngestService;
import com.nurseli.marketdata.application.ViopIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ViopDebtMarketScheduler {

    private final ViopIngestService viopIngestService;
    private final DebtIngestService debtIngestService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void ingestViopAndDebt() {
        try {
            viopIngestService.ingestLatest();
            log.info("[SCHEDULER] VIOP MVP snapshots ingested");
        } catch (Exception ex) {
            log.warn("[SCHEDULER] VIOP ingest failed: {}", ex.getMessage());
        }
        try {
            debtIngestService.ingestLatest();
            log.info("[SCHEDULER] Debt MVP snapshots ingested");
        } catch (Exception ex) {
            log.warn("[SCHEDULER] Debt ingest failed: {}", ex.getMessage());
        }
    }
}
