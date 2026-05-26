package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
@ConfigurationProperties(prefix = "market.loan-rates")
@Data
public class LoanRatesProperties {

    private boolean enabled = true;

    private Backfill backfill = new Backfill();

    /** Varsayılan: her Pazartesi 07:05 Europe/Istanbul. */
    private boolean schedulerEnabled = true;
    private String schedulerCron = "0 5 7 * * MON";

    @Data
    public static class Backfill {
        private boolean enabled = true;
        private LocalDate from = LocalDate.of(2020, 1, 1);
    }
}
