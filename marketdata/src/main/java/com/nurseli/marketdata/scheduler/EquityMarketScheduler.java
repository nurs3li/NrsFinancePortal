package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.EquityPriceIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EquityMarketScheduler {

    private final EquityPriceIngestService equityPriceIngestService;

    @Scheduled(fixedDelay = 600_000) // 10 dakika
    public void fetchEquityQuotes() {
        log.info("[SCHEDULER] Fetching equity quotes from FinHub");
        equityPriceIngestService.fetchAndSaveEquityQuotes();
    }

    @Scheduled(cron = "${app.equity.history-incremental-cron:0 30 2 * * *}")
    public void fetchEquityDailyHistoryIncremental() {
        log.info("[SCHEDULER] Fetching equity daily history incrementally");
        equityPriceIngestService.fetchAndSaveIncrementalDailyHistory();
    }
}