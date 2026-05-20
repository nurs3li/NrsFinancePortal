package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.inflation.InflationIndexIngestService;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.InflationCpiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "market.inflation.cpi", name = "scheduler-enabled", havingValue = "true")
public class InflationCpiSyncScheduler {

    private final EvdsProperties evdsProperties;
    private final InflationCpiProperties inflationCpiProperties;
    private final InflationIndexIngestService inflationIndexIngestService;

    @Scheduled(cron = "${market.inflation.cpi.scheduler-cron:0 0 7 3 * *}")
    public void syncRollingWindow() {
        if (!evdsProperties.isEnabled() || !inflationCpiProperties.isPersistEnabled()) {
            return;
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusMonths(36).withDayOfMonth(1);
        var r = inflationIndexIngestService.backfill(from, to, true, false);
        log.info("[INFLATION_CPI] scheduled sync: {}", r);
    }
}
