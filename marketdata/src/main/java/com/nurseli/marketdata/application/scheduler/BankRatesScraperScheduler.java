package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.bankfx.BankFxIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BankRatesScraperScheduler {

    private final BankFxIngestService ingestService;

    /** Her 2 saatte bir (Europe/Istanbul). Startup çekimi ayrı runner'da. */
    @Scheduled(cron = "${app.market.bank-rates.cron:0 0 */2 * * *}", zone = "${app.market.bank-rates.zone:Europe/Istanbul}")
    public void scheduledScrape() {
        ingestService.scrapeAndPersist("cron");
    }
}
