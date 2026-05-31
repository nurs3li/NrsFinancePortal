package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.CryptoHistoryBackfillProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoFlatCandleRepairRunner implements ApplicationRunner {

    private final CryptoHistoryBackfillProperties properties;
    private final CryptoPriceIngestService cryptoPriceIngestService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isRepairFlatCandlesOnStartup()) {
            return;
        }
        Thread repairThread = new Thread(() -> {
            try {
                log.info("[CRYPTO_HISTORY] repair_flat startup async begin");
                cryptoPriceIngestService.repairFlatDailyCandles();
                log.info("[CRYPTO_HISTORY] repair_flat startup async done");
            } catch (Exception ex) {
                log.warn("[CRYPTO_HISTORY] repair_flat startup async failed reason={}", ex.getMessage(), ex);
            }
        }, "crypto-flat-candle-repair");
        repairThread.setDaemon(true);
        repairThread.start();
    }
}
