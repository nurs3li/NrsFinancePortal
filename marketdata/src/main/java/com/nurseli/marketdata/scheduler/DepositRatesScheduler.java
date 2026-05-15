package com.nurseli.marketdata.scheduler;

import com.nurseli.marketdata.application.deposit.DepositRatesIngestService;
import com.nurseli.marketdata.config.DepositRatesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "market.deposit-rates.scheduler-enabled", havingValue = "true")
public class DepositRatesScheduler {

    private final DepositRatesProperties depositRatesProperties;
    private final DepositRatesIngestService depositRatesIngestService;

    @Scheduled(cron = "${market.deposit-rates.scheduler-cron:0 0 7 * * MON}")
    public void incrementalIngest() {
        if (!depositRatesProperties.isEnabled()) {
            return;
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusWeeks(8);
        var r = depositRatesIngestService.ingestRange(from, to);
        log.info("[DEPOSIT_RATES_SCHED] incremental status={} upserted={}", r.status(), r.pointsUpserted());
    }
}
