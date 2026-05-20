package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.MarketType;
import com.nurseli.marketdata.config.MarketStaleTailProperties;
import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import com.nurseli.marketdata.domain.price.FxDailyCandle;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.repository.CryptoDailyCandleRepository;
import com.nurseli.marketdata.repository.DebtSnapshotRepository;
import com.nurseli.marketdata.repository.EquityDailyCandleRepository;
import com.nurseli.marketdata.repository.FxDailyCandleRepository;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * BIST {@link com.nurseli.marketdata.application.bist.BistEquityQueryService#maybeIngestStaleTail} ile aynı fikir:
 * veri varken ama güncel değilken okuma yolunda eksik günleri doldur.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketStaleTailRepairService {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

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
    private final MarketMetalsIsyatirimProperties marketMetalsIsyatirimProperties;
    private final DebtIngestService debtIngestService;

    public void repairBeforeRead(MarketType type, String symbol, LocalDate requestedTo) {
        if (!staleTailProperties.isEnabled() || symbol == null || symbol.isBlank()) {
            return;
        }
        String sym = symbol.trim().toUpperCase();
        LocalDate targetTo = effectiveTo(requestedTo);
        switch (type) {
            case EQUITY -> repairEquity(sym, targetTo);
            case CRYPTO -> repairCrypto(sym, targetTo);
            case FX -> repairFx(sym, targetTo);
            case METALS -> repairMetal(sym, targetTo);
            default -> {}
        }
    }

    public void repairDebtSnapshotsIfStale() {
        if (!staleTailProperties.isEnabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(20);
        Optional<LocalDateTime> maxAsOf =
                debtSnapshotRepository.findAll().stream()
                        .map(DebtSnapshot::getAsOf)
                        .filter(a -> a != null)
                        .max(Comparator.naturalOrder());
        if (maxAsOf.isPresent() && !maxAsOf.get().isBefore(cutoff)) {
            return;
        }
        try {
            log.info("[STALE_TAIL] debt ingestLatest maxAsOf={}", maxAsOf.orElse(null));
            debtIngestService.ingestLatest();
        } catch (Exception ex) {
            log.warn("[STALE_TAIL] debt ingest failed reason={}", ex.getMessage());
        }
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
        if ("XAU_TRY".equals(symbol)) {
            Optional<MarketPriceHistory> latest =
                    marketPriceHistoryRepository.findTopBySymbolOrderByTimestampDesc(symbol);
            LocalDate lastDay = latest.map(r -> r.getTimestamp().toLocalDate()).orElse(null);
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
}
