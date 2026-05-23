package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.api.dto.BistBackfillRequest;
import com.nurseli.marketdata.application.bist.BistEquityBackfillService;
import com.nurseli.marketdata.config.BistProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * BIST günlük (HisseTekil) verisini {@code market_price_history} tablosuna yazar.
 * {@link BistProperties#isSchedulerEnabled()} açıkken çalışır.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BistEquityDailyScheduler {

    private final BistProperties bistProperties;
    private final BistEquityBackfillService bistEquityBackfillService;

    @Scheduled(cron = "${market.bist.daily-ingest-cron}", zone = "${market.bist.scheduler-zone}")
    public void incrementalDailyBackfill() {
        runIncrementalBackfill("morning");
    }

    @Scheduled(cron = "${market.bist.daily-ingest-cron-close}", zone = "${market.bist.scheduler-zone}")
    public void closeSessionDailyBackfill() {
        runIncrementalBackfill("close");
    }

    private void runIncrementalBackfill(String slot) {
        if (!bistProperties.isEnabled() || !bistProperties.isSchedulerEnabled()) {
            return;
        }
        ZoneId zone = ZoneId.of(bistProperties.getSchedulerZone());
        LocalDate to = LocalDate.now(zone);
        LocalDate from = to.minusDays(Math.max(1, bistProperties.getSchedulerIncrementalLookbackDays()));
        BistBackfillRequest req = new BistBackfillRequest();
        req.setFrom(from);
        req.setTo(to);
        log.info("[BIST_DAILY_SCHED] incremental slot={} from={} to={}", slot, from, to);
        try {
            bistEquityBackfillService.runBackfill(req);
        } catch (Exception ex) {
            log.warn("[BIST_DAILY_SCHED] failed slot={} reason={}", slot, ex.getMessage());
        }
    }
}
