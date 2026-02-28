package com.nurseli.marketdata.runner;

import com.nurseli.marketdata.application.CryptoPriceIngestService;
import com.nurseli.marketdata.application.MarketPriceIngestService;
import com.nurseli.marketdata.application.MetalPriceIngestService;
import com.nurseli.marketdata.application.NewsIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Uygulama açılır açılmaz tüm piyasa verilerini bir kez çeker.
 * Böylece ilk sayfa açılışında DB'de güncel veri olur (döviz, kripto, altın).
 * Fon verisi zaten FundMarketScheduler.fetchYesterdayOnStartup ile yükleniyor.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class StartupDataLoader implements ApplicationRunner {

    private final MarketPriceIngestService marketPriceIngestService;
    private final CryptoPriceIngestService cryptoPriceIngestService;
    private final MetalPriceIngestService metalPriceIngestService;
    private final NewsIngestService newsIngestService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[STARTUP] Loading initial market data (FX, Crypto, Metal, News)...");

        runSafe("TCMB (Döviz)", marketPriceIngestService::fetchAndSaveTcmbRates);
        runSafe("CoinGecko (Kripto)", cryptoPriceIngestService::fetchAndSaveCryptoPrices);
        runSafe("CoinGecko (Altın)", metalPriceIngestService::fetchAndSaveGramGold);


        log.info("[STARTUP] Initial market data load finished.");
    }

    private void runSafe(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[STARTUP] {} failed: {}", name, e.getMessage());
        }
    }
}