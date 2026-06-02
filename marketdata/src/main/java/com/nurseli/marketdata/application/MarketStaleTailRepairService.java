package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.ingest.CryptoPriceIngestService;
import com.nurseli.marketdata.application.ingest.DebtIngestService;
import com.nurseli.marketdata.application.ingest.EquityPriceIngestService;
import com.nurseli.marketdata.application.ingest.IsYatirimMetalUsdIngestService;
import com.nurseli.marketdata.application.ingest.MarketPriceIngestService;
import com.nurseli.marketdata.application.ingest.MetalHistoryWarmupService;
import com.nurseli.marketdata.application.ingest.MetalPriceIngestService;
import com.nurseli.marketdata.api.dto.MarketType;
import com.nurseli.marketdata.config.MarketStaleTailProperties;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import com.nurseli.marketdata.domain.price.FxDailyCandle;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.persistence.CryptoDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.DebtSnapshotRepository;
import com.nurseli.marketdata.infrastructure.persistence.EquityDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.FxDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BIST {@link com.nurseli.marketdata.application.bist.BistEquityQueryService#maybeIngestStaleTail} ile aynı fikir:
 * veri varken ama güncel değilken okuma yolunda eksik günleri doldur.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketStaleTailRepairService {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");
    private final Set<String> inFlightRepairs = ConcurrentHashMap.newKeySet();
    private final ExecutorService repairExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "market-stale-tail-repair");
        thread.setDaemon(true);
        return thread;
    });

    private final MarketStaleTailProperties staleTailProperties;
    private final EquityDailyCandleRepository equityDailyCandleRepository;
    private final CryptoDailyCandleRepository cryptoDailyCandleRepository;
    private final FxDailyCandleRepository fxDailyCandleRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;
    private final DebtSnapshotRepository debtSnapshotRepository;
    private final EquityPriceIngestService equityPriceIngestService;
    private final CryptoPriceIngestService cryptoPriceIngestService;
    private final MarketPriceIngestService marketPriceIngestService;
    private final MetalPriceIngestService metalPriceIngestService;
    private final IsYatirimMetalUsdIngestService isYatirimMetalUsdIngestService;
    private final MetalHistoryWarmupService metalHistoryWarmupService;
    private final MarketMetalsIsyatirimProperties marketMetalsIsyatirimProperties;
    private final DebtIngestService debtIngestService;

    public void repairBeforeRead(MarketType type, String symbol, LocalDate requestedTo) {
        if (!staleTailProperties.isEnabled() || symbol == null || symbol.isBlank()) {
            return;
        }
        String sym = symbol.trim().toUpperCase();
        LocalDate targetTo = effectiveTo(requestedTo);
        String repairKey = type.name() + ":" + sym + ":" + targetTo;
        scheduleRepair(repairKey, () -> {
            switch (type) {
                case EQUITY -> repairEquity(sym, targetTo);
                case CRYPTO -> repairCrypto(sym, targetTo);
                case FX -> repairFx(sym, targetTo);
                case METALS -> repairMetal(sym, targetTo);
                default -> {
                }
            }
        });
    }

    public void repairDebtSnapshotsIfStale() {
        if (!staleTailProperties.isEnabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(20);
        Optional<LocalDateTime> maxAsOf = debtSnapshotRepository.findMaxAsOf();
        if (maxAsOf.isPresent() && !maxAsOf.get().isBefore(cutoff)) {
            return;
        }
        scheduleRepair("DEBT_SNAPSHOTS", () -> {
            try {
                log.info("[STALE_TAIL] debt ingestLatest maxAsOf={}", maxAsOf.orElse(null));
                debtIngestService.ingestLatest();
            } catch (Exception ex) {
                log.warn("[STALE_TAIL] debt ingest failed reason={}", ex.getMessage());
            }
        });
    }

    private void scheduleRepair(String repairKey, Runnable repairTask) {
        if (!inFlightRepairs.add(repairKey)) {
            return;
        }
        repairExecutor.execute(() -> {
            try {
                repairTask.run();
            } finally {
                inFlightRepairs.remove(repairKey);
            }
        });
    }

    private void repairEquity(String symbol, LocalDate targetTo) {
        Optional<EquityDailyCandle> latest = equityDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(symbol);
        if (!isDailyStale(latest.map(EquityDailyCandle::getAsOf), targetTo)) {
            return;
        }
        try {
            log.info(
                    "[STALE_TAIL] equity symbol={} lastDay={} targetTo={}",
                    symbol,
                    latest.map(EquityDailyCandle::getAsOf).orElse(null),
                    targetTo);
            equityPriceIngestService.ingestIncrementalForSymbol(symbol);
        } catch (Exception ex) {
            log.warn("[STALE_TAIL] equity failed symbol={} reason={}", symbol, ex.getMessage());
        }
    }

    private void repairCrypto(String symbol, LocalDate targetTo) {
        Optional<CryptoDailyCandle> latest = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(symbol);
        if (!isDailyStale(latest.map(CryptoDailyCandle::getAsOf), targetTo)) {
            return;
        }
        try {
            log.info(
                    "[STALE_TAIL] crypto symbol={} lastDay={} targetTo={}",
                    symbol,
                    latest.map(CryptoDailyCandle::getAsOf).orElse(null),
                    targetTo);
            cryptoPriceIngestService.ingestIncrementalForSymbol(symbol);
        } catch (Exception ex) {
            log.warn("[STALE_TAIL] crypto failed symbol={} reason={}", symbol, ex.getMessage());
        }
    }

    private void repairFx(String symbol, LocalDate targetTo) {
        Optional<FxDailyCandle> latest = fxDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(symbol);
        if (!isDailyStale(latest.map(FxDailyCandle::getAsOf), targetTo)) {
            return;
        }
        try {
            log.info(
                    "[STALE_TAIL] fx symbol={} lastDay={} targetTo={}",
                    symbol,
                    latest.map(FxDailyCandle::getAsOf).orElse(null),
                    targetTo);
            marketPriceIngestService.fetchAndSaveFxHistoryBackfill(45);
        } catch (Exception ex) {
            log.warn("[STALE_TAIL] fx failed symbol={} reason={}", symbol, ex.getMessage());
        }
    }

    private void repairMetal(String symbol, LocalDate targetTo) {
        LocalDate warmupFrom = targetTo.minusYears(Math.max(1, marketMetalsIsyatirimProperties.getDefaultLookbackYears()));
        if ("XAU_TRY".equals(symbol)) {
            Optional<MarketPriceHistory> latest =
                    marketPriceHistoryRepository.findTopBySymbolOrderByTimestampDesc(symbol);
            LocalDate lastDay = latest.map(r -> r.getTimestamp().toLocalDate()).orElse(null);
            if (lastDay == null) {
                try {
                    log.info("[STALE_TAIL] XAU_TRY empty_db warmupFrom={} targetTo={}", warmupFrom, targetTo);
                    metalHistoryWarmupService.warmupSymbolSync(symbol, warmupFrom, targetTo, "stale-tail-empty-db");
                } catch (Exception ex) {
                    log.warn("[STALE_TAIL] XAU_TRY empty_db failed reason={}", ex.getMessage());
                }
                return;
            }
            if (isDailyStale(Optional.ofNullable(lastDay), targetTo)) {
                try {
                    int days = (int) Math.min(90, Math.max(14, targetTo.toEpochDay() - (lastDay != null ? lastDay.toEpochDay() : targetTo.toEpochDay()) + 5));
                    log.info("[STALE_TAIL] XAU_TRY lastDay={} backfillDays={}", lastDay, days);
                    metalPriceIngestService.ensureHistoricalBackfill(days);
                    metalPriceIngestService.fetchAndSaveGramGold();
                } catch (Exception ex) {
                    log.warn("[STALE_TAIL] XAU_TRY failed reason={}", ex.getMessage());
                }
            }
            return;
        }
        if (!PreciousMetalUsdCatalog.isUsdOunceMetal(symbol)) {
            return;
        }
        if (!marketMetalsIsyatirimProperties.isEnabled()) {
            return;
        }
        Optional<MarketPriceHistory> latest =
                marketPriceHistoryRepository.findTopBySymbolAndSourceOrderByTimestampDesc(
                        symbol, PreciousMetalUsdCatalog.SOURCE);
        LocalDate lastDay = latest.map(r -> r.getTimestamp().toLocalDate()).orElse(null);
        if (lastDay == null) {
            try {
                log.info("[STALE_TAIL] metal-usd empty_db symbol={} warmupFrom={} targetTo={}", symbol, warmupFrom, targetTo);
                metalHistoryWarmupService.warmupSymbolSync(symbol, warmupFrom, targetTo, "stale-tail-empty-db");
            } catch (Exception ex) {
                log.warn("[STALE_TAIL] metal-usd empty_db failed symbol={} reason={}", symbol, ex.getMessage());
            }
            return;
        }
        if (!isDailyStale(Optional.ofNullable(lastDay), targetTo)) {
            return;
        }
        PreciousMetalUsdCatalog.Entry entry = PreciousMetalUsdCatalog.byCanonicalOrNull(symbol);
        if (entry != null) {
            try {
                log.info("[STALE_TAIL] metal-usd symbol={} lastDay={}", symbol, lastDay);
                isYatirimMetalUsdIngestService.refreshLatestForSymbol(entry);
            } catch (Exception ex) {
                log.warn("[STALE_TAIL] metal-usd failed symbol={} reason={}", symbol, ex.getMessage());
            }
        }
    }

    private boolean isDailyStale(Optional<LocalDate> lastDayOpt, LocalDate targetTo) {
        if (lastDayOpt.isEmpty()) {
            return false;
        }
        LocalDate lastDay = lastDayOpt.get();
        LocalDate cutoff = targetTo.minusDays(Math.max(1, staleTailProperties.getDays()));
        return !lastDay.isAfter(cutoff);
    }

    private static LocalDate effectiveTo(LocalDate requestedTo) {
        LocalDate today = LocalDate.now(IST);
        if (requestedTo == null || requestedTo.isAfter(today)) {
            return today;
        }
        return requestedTo;
    }

    @PreDestroy
    void shutdownExecutor() {
        repairExecutor.shutdownNow();
    }
}
