package com.nurseli.marketdata.config;

import com.nurseli.marketdata.application.deposit.DepositRatesIngestService;
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
@ConditionalOnProperty(name = "market.deposit-rates.enabled", havingValue = "true")
public class DepositRatesStartupBackfill {

    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesIngestService depositRatesIngestService;
    private final AtomicBoolean ran = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!depositRatesProperties.getBackfill().isEnabled()) {
            return;
        }
        if (!ran.compareAndSet(false, true)) {
            return;
        }
        LocalDate from = depositRatesProperties.getBackfill().getFrom();
        LocalDate to = LocalDate.now();
        if (from == null || to.isBefore(from)) {
            log.warn("[DEPOSIT_RATES] startup backfill skipped: invalid from={} to={}", from, to);
            return;
        }
        var r = depositRatesIngestService.ingestRange(from, to);
        log.info("[DEPOSIT_RATES] startup backfill status={} upserted={} skipped={}", r.status(), r.pointsUpserted(), r.pointsSkipped());
    }
}
