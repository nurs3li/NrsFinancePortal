package com.nurseli.marketdata.application.scheduler;

import com.nurseli.marketdata.application.ingest.EquityPriceIngestService;
import com.nurseli.marketdata.config.EquityInitialSeedProperties;
import com.nurseli.marketdata.config.EquityProperties;
import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import com.nurseli.marketdata.infrastructure.persistence.EquityDailyCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class EquityMarketScheduler {

    private static final int STARTUP_HISTORY_BATCH = 20;

    private final EquityPriceIngestService equityPriceIngestService;
    private final EquityDailyCandleRepository equityDailyCandleRepository;
    private final EquityProperties equityProperties;
    private final EquityInitialSeedProperties initialSeedProperties;

    /**
     * Önce (gerekirse) dış kaynaktan geçmiş OHLC bir kez doldurulur; sonra incremental + Finnhub quote rollup ile devam edilir.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void equityDailyHistoryOnStartup() {
        try {
            if (initialSeedProperties.isEnabled() && needsHistorySeed()) {
                int seedDays = Math.max(1, equityProperties.getMaxHistoryDays());
                log.info("[EQUITY][STARTUP] One-shot history backfill ({} days) then incremental + quote rollup",
                        seedDays);
                equityPriceIngestService.fetchAndSaveHistoryBackfill(seedDays, STARTUP_HISTORY_BATCH);
            } else if (initialSeedProperties.isEnabled()) {
                log.info("[EQUITY][STARTUP] History seed skipped (DB already warm: minCandles={}, staleDays={})",
                        initialSeedProperties.getMinCandles(), initialSeedProperties.getStaleDays());
            }
            log.info("[EQUITY][STARTUP] Incremental daily history catch-up (one-shot)");
            equityPriceIngestService.fetchAndSaveIncrementalDailyHistory();
        } catch (Exception ex) {
            log.warn("[EQUITY][STARTUP] Startup equity history failed: {}", ex.getMessage());
        }
    }

    private boolean needsHistorySeed() {
        List<String> symbols = equityProperties.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            return false;
        }
        LocalDate today = LocalDate.now();
        int min = Math.max(1, initialSeedProperties.getMinCandles());
        int stale = Math.max(1, initialSeedProperties.getStaleDays());
        LocalDate staleCutoff = today.minusDays(stale);
        for (String raw : symbols) {
            String sym = raw == null ? "" : raw.trim().toUpperCase();
            if (sym.isEmpty()) {
                continue;
            }
            if (equityDailyCandleRepository.countBySymbol(sym) < min) {
                log.info("[EQUITY][SEED] {} needs seed: candle count below {}", sym, min);
                return true;
            }
            Optional<EquityDailyCandle> top = equityDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(sym);
            if (top.isEmpty()) {
                log.info("[EQUITY][SEED] {} needs seed: no equity_daily_candle rows", sym);
                return true;
            }
            if (top.get().getAsOf().isBefore(staleCutoff)) {
                log.info("[EQUITY][SEED] {} needs seed: last as_of {} is before {}", sym, top.get().getAsOf(), staleCutoff);
                return true;
            }
        }
        return false;
    }

    @Scheduled(fixedDelay = 3_600_000) // 1 saat
    public void fetchEquityQuotes() {
        log.info("[SCHEDULER] Fetching equity quotes from FinHub");
        equityPriceIngestService.fetchAndSaveEquityQuotes();
    }

    @Scheduled(cron = "${app.equity.history-incremental-cron:0 30 2 * * *}", zone = "${app.equity.history-incremental-zone:Europe/Istanbul}")
    public void fetchEquityDailyHistoryIncremental() {
        log.info("[SCHEDULER] Fetching equity daily history incrementally");
        equityPriceIngestService.fetchAndSaveIncrementalDailyHistory();
    }
}