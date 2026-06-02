package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.ingest.TefasFundIngestService;
import com.nurseli.marketdata.config.TefasProperties;
import com.nurseli.marketdata.infrastructure.persistence.TefasFundProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class TefasFundScheduler {

    private final AtomicBoolean startupTriggered = new AtomicBoolean(false);

    private final TefasFundIngestService ingestService;
    private final TefasProperties properties;
    private final TefasFundProfileRepository profileRepository;

    /** BIST/ETF startup seed'leri bitene kadar beklememek için arka planda çalışır. */
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleStartupBackfill() {
        if (!startupTriggered.compareAndSet(false, true)) {
            return;
        }
        Thread worker =
                new Thread(this::runStartupBackfill, "tefas-startup-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    private void runStartupBackfill() {
        if (!properties.isEnabled()) {
            log.info("[TEFAS][STARTUP] Disabled");
            return;
        }
        List<String> symbols = properties.normalizedSymbols();
        if (symbols.isEmpty()) {
            log.warn("[TEFAS][STARTUP] No symbols configured (app.tefas.symbols)");
            return;
        }

        if (properties.isSyncProfilesOnStartup()) {
            long profileCount = profileRepository.findByCodeIn(symbols).size();
            if (profileCount < symbols.size()) {
                log.info(
                        "[TEFAS][STARTUP] Profile sync (have {}/{}), 1 bulk API filtered",
                        profileCount,
                        symbols.size());
                ingestService.syncProfilesFromTefas(symbols);
            }
        }

        if (!properties.isBackfillOnStartup()) {
            return;
        }

        int min = Math.max(1, properties.getMinHistoryRowsBeforeSkip());
        int months = Math.max(1, properties.getHistoryBackfillMonths());
        for (String code : symbols) {
            long existing = ingestService.countNavRows(code);
            if (existing >= min) {
                log.info("[TEFAS][STARTUP] Skip backfill symbol={} rows={}", code, existing);
                continue;
            }
            log.info("[TEFAS][STARTUP] Backfill symbol={} months={} (existingRows={})", code, months, existing);
            int inserted = ingestService.ingestHistory(code, months);
            if (inserted == 0 && existing == 0) {
                log.warn("[TEFAS][STARTUP] No NAV ingested for symbol={} — TEFAS'ta geçersiz/aktif olmayan kod olabilir", code);
            }
        }
        log.info("[TEFAS][STARTUP] Backfill finished for symbols={}", symbols);
    }

    @Scheduled(cron = "${market.tefas.daily-cron:0 35 7 * * *}", zone = "Europe/Istanbul")
    public void fetchDailyNav() {
        if (!properties.isEnabled()) {
            return;
        }
        List<String> symbols = properties.normalizedSymbols();
        if (symbols.isEmpty()) {
            return;
        }
        log.info("[TEFAS][SCHEDULED] Daily NAV for {} fund(s)", symbols.size());
        for (String code : symbols) {
            ingestService.ingestLatestNav(code.trim().toUpperCase(Locale.ROOT));
        }
    }
}
