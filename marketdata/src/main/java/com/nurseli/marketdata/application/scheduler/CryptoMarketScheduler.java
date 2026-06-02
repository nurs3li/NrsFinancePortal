package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.ingest.CryptoPriceIngestService;
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
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void fetchCryptoPrices() {
        log.info("[SCHEDULER] Fetching crypto prices from CoinGecko");
        ingestService.fetchAndSaveCryptoPrices();
    }

    @Scheduled(cron = "${app.crypto.history-incremental-cron:0 45 2 * * *}")
    public void fetchCryptoDailyHistoryIncremental() {
        log.info("[SCHEDULER] Fetching incremental crypto OHLC history");
        ingestService.fetchAndSaveIncrementalDailyHistory();
    }
}