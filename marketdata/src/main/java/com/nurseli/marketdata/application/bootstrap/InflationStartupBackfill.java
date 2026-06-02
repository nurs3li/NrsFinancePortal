package com.nurseli.marketdata.application.bootstrap;

import com.nurseli.marketdata.application.inflation.InflationIndexIngestService;
import com.nurseli.marketdata.config.InflationBackfillProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "market.inflation.backfill", name = "startup-enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "market.bootstrap", name = "orchestrate-startup", havingValue = "false")
public class InflationStartupBackfill {

    private final InflationBackfillProperties inflationBackfillProperties;
    private final InflationIndexIngestService inflationIndexIngestService;
    private final AtomicBoolean ran = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!inflationBackfillProperties.isEnabled()) {
            log.info("[INFLATION] startup backfill skipped: backfill.enabled=false");
            return;
        }
        if (!ran.compareAndSet(false, true)) {
            return;
        }
        LocalDate from = inflationBackfillProperties.getFrom();
        LocalDate to = LocalDate.now();
        if (from == null || to.isBefore(from)) {
            log.warn("[INFLATION] startup backfill skipped: invalid from={} to={}", from, to);
            return;
        }
        var r = inflationIndexIngestService.backfill(from, to);
        log.info("[INFLATION] startup backfill complete status={} series={}", r.status(), r.series());
    }
}
