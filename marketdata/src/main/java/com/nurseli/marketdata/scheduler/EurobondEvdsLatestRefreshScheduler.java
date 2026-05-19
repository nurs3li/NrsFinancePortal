package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.eurobond.EurobondEvdsIngestService;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.market.eurobonds.evds.latest-refresh",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EurobondEvdsLatestRefreshScheduler {

    private final EurobondEvdsProperties properties;
    private final EurobondEvdsIngestService ingestService;

    @Scheduled(fixedDelayString = "${app.market.eurobonds.evds.latest-refresh.fixed-delay-ms:86400000}")
    public void refreshLatest() {
        if (!properties.isEnabled()) {
            return;
        }
        var r = ingestService.ingestRecentWeeks(12);
        log.info("[EUROBOND_EVDS_SCHED] status={} upserted={}", r.status(), r.pointsUpserted());
    }
}
