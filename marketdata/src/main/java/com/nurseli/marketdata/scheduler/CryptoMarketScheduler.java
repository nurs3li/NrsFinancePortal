package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.CryptoPriceIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoMarketScheduler {

    private final CryptoPriceIngestService ingestService;

    /**
     * Crypto prices (CoinGecko free tier rate limit için seyrek)
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void fetchCryptoPrices() {
        log.info("[SCHEDULER] Fetching crypto prices from CoinGecko");
        ingestService.fetchAndSaveCryptoPrices();
    }
}