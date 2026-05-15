package com.nurseli.marketdata.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "market.deposit-rates")
@Data
public class DepositRatesProperties {

    private boolean enabled = false;

    private Backfill backfill = new Backfill();

    /** WEEKLY — kredi faizi (haftalık akım) ile aynı frekans ailesi; ingest EVDS haftalık ham seriyi işler. */
    private String frequency = "WEEKLY";

    private boolean schedulerEnabled = false;

    /** Varsayılan: her Pazartesi 07:00 Europe/Istanbul (app TZ). */
    private String schedulerCron = "0 0 7 * * MON";

    private List<SeriesEntry> series = new ArrayList<>();

    @PostConstruct
    void ensureSeriesList() {
        if (series == null) {
            series = new ArrayList<>();
        }
    }

    @Data
    public static class Backfill {
        private boolean enabled = false;
        private LocalDate from = LocalDate.of(2018, 1, 1);
    }

    @Data
    public static class SeriesEntry {
        /**
         * Opsiyonel: API meta / debug için mantıksal gösterge (örn. DEPOSIT_RATE_TRY_1M_WEEKLY).
         * Boş bırakılırsa yalnızca {@link #seriesCode} kullanılır.
         */
        private String logicalIndicatorCode;
        private String seriesCode;
        private String currency;
        private String term;
    }
}
