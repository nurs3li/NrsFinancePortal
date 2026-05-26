package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.market.metals.history-warmup")
@Data
public class MarketMetalsHistoryWarmupProperties {

    private boolean enabled = true;
    private int maxRangeChunkDays = 180;
    private long delayBetweenChunksMs = 350L;
}
