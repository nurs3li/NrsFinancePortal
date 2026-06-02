package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.ingest.MarketPriceIngestService;
import com.nurseli.marketdata.application.ingest.EvdsIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FxMarketScheduler {

    private final MarketPriceIngestService ingestService;
    private final EvdsIngestService evdsIngestService;

    /**
     * FX rates (TCMB) — varsayılan 1 saat; ingest DB + bellek snapshot günceller.
     * {@code app.market.fx.scheduler-ms} ile özelleştirilebilir.
     */
    @Scheduled(fixedDelayString = "${app.market.fx.scheduler-ms:3600000}")
    public void fetchFxRates() {
        try {
            log.info("[SCHEDULER] Fetching FX rates from TCMB");
            ingestService.fetchAndSaveTcmbRates();
        } catch (Exception ex) {
            log.warn("[SCHEDULER] TCMB ingest failed, fallback providers will serve data. reason={}", ex.getMessage());
        }
        try {
            evdsIngestService.fetchAndSaveRecentFxHistory();
        } catch (Exception ex) {
            log.warn("[SCHEDULER] EVDS ingest failed. reason={}", ex.getMessage());
        }
        try {
            ingestService.fetchAndSaveFxHistoryIncremental();
        } catch (Exception ex) {
            log.debug("[SCHEDULER] FX OHLC incremental failed. reason={}", ex.getMessage());
        }
    }
}