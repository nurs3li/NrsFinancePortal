package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.fx.history-backfill")
public class FxHistoryBackfillProperties {
    private boolean enabled = false;
    private int periodDays = 730;
    private boolean shutdownOnComplete = false;
}
