package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.DebtIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Borç enstrümanları için periyodik ingest (VIOP canlı akışı ayrı servislerde).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViopDebtMarketScheduler {

    private final DebtIngestService debtIngestService;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void ingestDebt() {
        try {
            debtIngestService.ingestLatest();
            log.info("[SCHEDULER] Debt MVP snapshots ingested");
        } catch (Exception ex) {
            log.warn("[SCHEDULER] Debt ingest failed: {}", ex.getMessage());
        }
    }
}
