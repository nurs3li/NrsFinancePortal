package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.loan.LoanRatesMacroService;
import com.nurseli.marketdata.config.LoanRatesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "market.loan-rates.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class LoanRatesScheduler {

    private final LoanRatesProperties loanRatesProperties;
    private final LoanRatesMacroService loanRatesMacroService;

    @Scheduled(cron = "${market.loan-rates.scheduler-cron:0 5 7 * * MON}", zone = "Europe/Istanbul")
    public void incrementalIngest() {
        if (!loanRatesProperties.isEnabled()) {
            return;
        }
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusWeeks(8);
        var result = loanRatesMacroService.ingestRange(from, to);
        log.info("[LOAN_RATES_SCHED] incremental status={} upserted={}", result.status(), result.pointsUpserted());
    }
}
