package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market.inflation.ppi")
@Data
public class InflationPpiProperties {
    /**
     * EVDS’ten çekilen Yİ-ÜFE satırlarının DB’ye yazılması (idempotent upsert).
     */
    private boolean persistEnabled = true;
    private int baseYear = 2003;
    private boolean schedulerEnabled = false;
    /** Spring 6-alan cron: varsayılan her ayın 3’ü 07:00. */
    private String schedulerCron = "0 0 7 3 * *";
}
