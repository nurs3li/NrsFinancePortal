package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.IsYatirimMetalUsdIngestService;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.market.metals.isyatirim.enabled", havingValue = "true", matchIfMissing = true)
public class IsyatirimMetalUsdLatestScheduler {

    private final MarketMetalsIsyatirimProperties properties;
    private final IsYatirimMetalUsdIngestService ingestService;

    @Scheduled(
            fixedDelayString = "${app.market.metals.isyatirim.latest-refresh.fixed-delay-ms:3600000}",
            initialDelayString = "PT3M"
    )
    public void refreshLatest() {
        if (!properties.isEnabled() || !properties.getLatestRefresh().isEnabled()) {
            return;
        }
        ingestService.refreshLatestAll();
    }
}
