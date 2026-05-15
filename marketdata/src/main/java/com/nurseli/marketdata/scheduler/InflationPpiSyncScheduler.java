package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.inflation.InflationPpiIngestService;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "market.inflation.ppi", name = "scheduler-enabled", havingValue = "true")
public class InflationPpiSyncScheduler {

    private final EvdsProperties evdsProperties;
    private final InflationPpiProperties inflationPpiProperties;
    private final InflationPpiIngestService inflationPpiIngestService;

    @Scheduled(cron = "${market.inflation.ppi.scheduler-cron:0 0 7 3 * *}")
    public void syncRollingWindow() {
        if (!evdsProperties.isEnabled() || !inflationPpiProperties.isPersistEnabled()) {
            return;
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(36).withDayOfMonth(1);
        var r = inflationPpiIngestService.syncRange(from, to);
        log.info("[INFLATION_PPI] scheduled sync: {}", r);
    }
}
