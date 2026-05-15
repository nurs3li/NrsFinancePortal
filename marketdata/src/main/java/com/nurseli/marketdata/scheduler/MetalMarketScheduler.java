package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.MetalPriceIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetalMarketScheduler {

    private final MetalPriceIngestService ingestService;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    public void ingestMetals() {
        log.info("[METAL] Fetching metal prices from CoinGecko...");
        ingestService.fetchAndSaveGramGold();
    }
}