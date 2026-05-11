package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.DebtIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * VIOP artık yalnızca CSV backfill ile beslenir; canlı ingest yok.
 * Borç enstrümanları için periyodik ingest burada kalır.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViopDebtMarketScheduler {

    private final DebtIngestService debtIngestService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void ingestDebt() {
        try {
            debtIngestService.ingestLatest();
            log.info("[SCHEDULER] Debt MVP snapshots ingested");
        } catch (Exception ex) {
            log.warn("[SCHEDULER] Debt ingest failed: {}", ex.getMessage());
        }
    }
}
