package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@Configuration
@ConfigurationProperties(prefix = "market.inflation.backfill")
@Data
public class InflationBackfillProperties {
    private boolean enabled = false;
    private LocalDate from = LocalDate.of(2020, 1, 1);
    /** Uygulama ayağa kalkınca bir kez EVDS → DB doldurma (idempotent). */
    private boolean startupEnabled = false;
}
