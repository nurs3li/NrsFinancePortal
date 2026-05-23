package com.nurseli.marketdata.config;

import com.nurseli.marketdata.application.eurobond.EurobondEvdsIngestService;
import com.nurseli.marketdata.application.eurobond.EurobondInstrumentIngestService;
import com.nurseli.marketdata.infrastructure.persistence.EurobondWeeklyObservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnEurobondEvds
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.market.eurobonds.evds.backfill", name = "startup-enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnProperty(prefix = "market.bootstrap", name = "orchestrate-startup", havingValue = "false")
public class EurobondEvdsStartupBackfill {

    private final EurobondEvdsProperties properties;
    private final EurobondEvdsIngestService macroIngestService;
    private final EurobondInstrumentIngestService instrumentIngestService;
    private final EurobondWeeklyObservationRepository weeklyRepository;
    private final AtomicBoolean ran = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("[EUROBOND_STARTUP] listener fired enabled={} instrumentBackfill={}",
                properties.isEnabled(), properties.getInstruments().isStartupBackfillEnabled());
        if (!properties.isEnabled()) {
            log.info("[EUROBOND_STARTUP] skipped: evds disabled");
            return;
        }
        if (!ran.compareAndSet(false, true)) {
            log.info("[EUROBOND_STARTUP] skipped: already ran");
            return;
        }
        LocalDate from = properties.getBackfill().getFrom();
        LocalDate to = LocalDate.now();
        if (from == null || to.isBefore(from)) {
            log.warn("[EUROBOND_STARTUP] skipped invalid range from={} to={}", from, to);
            return;
        }
        long existing = weeklyRepository.count();
        if (existing < 20) {
            var macro = macroIngestService.ingestRange(from, to);
            log.info("[EUROBOND_STARTUP] macro backfill status={} upserted={} series={}",
                    macro.status(), macro.pointsUpserted(), macro.seriesTouched());
        } else {
            macroIngestService.ingestRecentWeeks(12);
            log.info("[EUROBOND_STARTUP] macro refresh recent weeks (existingRows={})", existing);
        }
        if (properties.getInstruments().isStartupBackfillEnabled()) {
            var ins = instrumentIngestService.ingestHistory(from, to);
            log.info("[EUROBOND_STARTUP] instruments status={} upserted={} touched={}",
                    ins.status(), ins.pointsUpserted(), ins.instrumentsTouched());
        }
    }
}
