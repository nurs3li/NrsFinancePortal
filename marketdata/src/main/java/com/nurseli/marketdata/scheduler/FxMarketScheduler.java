package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.MarketPriceIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FxMarketScheduler {

    private final MarketPriceIngestService ingestService;

    /**
     * FX rates (TCMB)
     * 5 dakikada bir güncellenir
     */
    @Scheduled(fixedDelay = 300_000)
    public void fetchFxRates() {
        log.info("[SCHEDULER] Fetching FX rates from TCMB");
        ingestService.fetchAndSaveTcmbRates();
    }
}