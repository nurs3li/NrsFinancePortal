package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.viop.backfill")
@Data
public class ViopBackfillProperties {
    private boolean enabled = false;
    private String dir = "artifacts/viop";
    private String pattern = "viop_*.csv";
    private boolean shutdownOnComplete = false;
}

