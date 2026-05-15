package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.equity.history-backfill")
public class EquityHistoryBackfillProperties {
    private boolean enabled = false;
    private int periodDays = 730;
    private int batchSize = 20;
    private boolean shutdownOnComplete = false;
}
