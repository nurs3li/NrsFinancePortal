package com.nurseli.marketdata.bootstrap;

import com.nurseli.marketdata.application.bankfx.BankFxIngestService;
import com.nurseli.marketdata.config.BankRatesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * İlk açılışta banka kuru çekimi — TCMB/VİOP/BIST startup yükünden sonra, ayrı thread + gecikme.
 */
@Component
@Order(2500)
@RequiredArgsConstructor
@Slf4j
public class BankRatesStartupRunner implements ApplicationRunner {

    private final BankRatesProperties properties;
    private final BankFxIngestService ingestService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isScrapeOnStartup()) {
            return;
        }
        long delayMs = Math.max(60_000L, properties.getStartupDelayMs());
        Thread worker = new Thread(() -> runDelayedScrape(delayMs), "bank-fx-startup-scrape");
        worker.setDaemon(true);
        worker.start();
        log.info("[BANK_FX] startup scrape scheduled delayMs={}", delayMs);
    }

    private void runDelayedScrape(long delayMs) {
        try {
            Thread.sleep(delayMs);
            ingestService.scrapeAndPersist("startup");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[BANK_FX] startup scrape interrupted");
        } catch (Exception ex) {
            log.warn("[BANK_FX] startup scrape failed reason={}", ex.getMessage());
        }
    }
}
