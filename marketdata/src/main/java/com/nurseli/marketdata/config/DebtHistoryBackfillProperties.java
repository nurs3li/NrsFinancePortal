package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.debt.history-backfill")
public class DebtHistoryBackfillProperties {
    private boolean enabled = false;
    private int periodDays = 730;
    private boolean shutdownOnComplete = false;
}
