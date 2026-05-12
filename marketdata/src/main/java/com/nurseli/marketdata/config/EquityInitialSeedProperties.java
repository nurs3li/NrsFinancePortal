package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Açılışta bir kez (koşul sağlanırsa) dış kaynaktan geçmiş OHLC doldurulur; ardından incremental + quote rollup devam eder.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.equity.initial-seed")
public class EquityInitialSeedProperties {

    /** Startup'ta ihtiyaç varsa {@code fetchAndSaveHistoryBackfill} çalışsın mı. */
    private boolean enabled = true;

    /** Sembol başına bu kadar günlük mum varsa ve tarih taze ise tohumlama atlanır. */
    private int minCandles = 120;

    /** Son mum bu günden eskiyse tohumlama / backfill tekrar denenir (boşluk onarımı). */
    private int staleDays = 4;
}
