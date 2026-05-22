package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.market.bank-rates")
public class BankRatesProperties {

    public static final String SOURCE_DOVIZBORSA = "DOVIZBORSA";

    private boolean enabled = true;
    private String url = "https://dovizborsa.com/banka/";
    /** Europe/Istanbul — her 2 saatte bir (saat başı, 0-22) */
    private String cron = "0 0 */2 * * *";
    private String zone = "Europe/Istanbul";
    private int staleAfterHours = 24;
    private String userAgent = "NrsFinancePortal/1.0";
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 20_000;
    /** Servis açılışında (gecikmeli thread) bir kez çek */
    private boolean scrapeOnStartup = true;
    /** Startup çekimi: diğer ingest/backfill işlerinden sonra (ms). Örn. 4 dk */
    private long startupDelayMs = 240_000;
    /** Ardışık çekimler arası minimum süre — startup ile cron çakışmasını önler */
    private long minIntervalBetweenScrapesMs = 300_000;
}
