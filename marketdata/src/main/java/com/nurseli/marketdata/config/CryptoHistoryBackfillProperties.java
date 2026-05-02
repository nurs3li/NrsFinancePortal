package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.crypto.history-backfill")
public class CryptoHistoryBackfillProperties {
    private boolean enabled = false;
    private int periodDays = 365;
    private boolean shutdownOnComplete = false;
}
