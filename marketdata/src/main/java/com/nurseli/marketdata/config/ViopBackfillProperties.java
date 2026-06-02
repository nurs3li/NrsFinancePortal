package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.viop.backfill")
@Data
public class ViopBackfillProperties {
    /** Uygulama açılışında {@link com.nurseli.marketdata.application.ingest.ViopBackfillRunner} bir kez çalıştırır. */
    private boolean enabled = false;
    private String dir = "artifacts/viop";
    private String pattern = "viop_*.csv";
    private boolean shutdownOnComplete = false;

    /** Periyodik tarama — klasöre yeni eklenen CSV dosyalarını otomatik DB'ye alır. */
    private Incremental incremental = new Incremental();

    @Data
    public static class Incremental {
        /** Periyodik tarama aktif mi? Tekrar süresi {@link #cron} ile belirlenir. */
        private boolean enabled = true;
        /**
         * Quartz/Spring cron formatı (saniye dakika saat gün ay haftalıkGün).
         * Default: her 3 saatte bir (00:00, 03:00, ..., 21:00 İstanbul saati).
         */
        private String cron = "0 0 */3 * * *";
        /** Cron zaman dilimi. */
        private String zone = "Europe/Istanbul";
    }
}

