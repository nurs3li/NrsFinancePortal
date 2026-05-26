package com.nurseli.marketdata.application;

import com.nurseli.marketdata.config.MarketMetalsHistoryWarmupProperties;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetalHistoryWarmupService {

    private static final ZoneId MARKET_WALL_CLOCK_ZONE = ZoneId.of("Europe/Istanbul");
    private static final String GRAM_GOLD = "XAU_TRY";
    private static final String GRAM_GOLD_ALIAS = "ALTIN_TRY";

    private final Set<String> inFlightWarmups = ConcurrentHashMap.newKeySet();
    private final ExecutorService warmupExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "metal-history-warmup");
        thread.setDaemon(true);
        return thread;
    });

    private final MarketMetalsHistoryWarmupProperties warmupProperties;
    private final MarketPriceHistoryRepository repository;
    private final MetalPriceIngestService metalPriceIngestService;
    private final IsYatirimMetalUsdIngestService isYatirimMetalUsdIngestService;

    public record MetalHistoryCoverage(
            String symbol,
            LocalDate from,
            LocalDate to,
            LocalDate oldestDay,
            LocalDate newestDay,
            long availableDays,
            long expectedDays,
            boolean ready
    ) {}

    public MetalHistoryCoverage getCoverage(String rawSymbol, LocalDate requestedFrom, LocalDate requestedTo) {
        String symbol = normalizeSymbol(rawSymbol);
        LocalDate from = requestedFrom;
        LocalDate to = effectiveTo(requestedTo);
        if (symbol == null || from == null || to == null || to.isBefore(from)) {
            return new MetalHistoryCoverage(rawSymbol, requestedFrom, requestedTo, null, null, 0, 0, false);
        }
        LocalDateTime start = from.atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay(MARKET_WALL_CLOCK_ZONE).toLocalDateTime();
        long availableDays = isUsdOunceSymbol(symbol)
                ? repository.countDistinctTradeDaysBySymbolAndSourceAndTimestampRange(
                        symbol,
                        PreciousMetalUsdCatalog.SOURCE,
                        start,
                        endExclusive)
                : repository.countDistinctTradeDaysBySymbolAndTimestampRange(symbol, start, endExclusive);
        long expectedDays = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate oldestDay = oldestDay(symbol).orElse(null);
        LocalDate newestDay = newestDay(symbol).orElse(null);
        boolean ready = oldestDay != null
                && newestDay != null
                && !oldestDay.isAfter(from)
                && !newestDay.isBefore(to)
                && availableDays >= expectedDays;
        return new MetalHistoryCoverage(symbol, from, to, oldestDay, newestDay, availableDays, expectedDays, ready);
    }

    public void requestWarmupIfMissing(String rawSymbol, LocalDate requestedFrom, LocalDate requestedTo, String reason) {
        if (!warmupProperties.isEnabled()) {
            return;
        }
        MetalHistoryCoverage coverage = getCoverage(rawSymbol, requestedFrom, requestedTo);
        if (coverage.ready()) {
            return;
        }
        String symbol = coverage.symbol();
        if (symbol == null || coverage.from() == null || coverage.to() == null) {
            return;
        }
        String warmupKey = warmupKey(symbol, coverage.from(), coverage.to());
        if (!inFlightWarmups.add(warmupKey)) {
            log.debug(
                    "[METAL_HISTORY_WARMUP] already_running symbol={} from={} to={} reason={}",
                    symbol,
                    coverage.from(),
                    coverage.to(),
                    reason);
            return;
        }
        log.info(
                "[METAL_HISTORY_WARMUP] queued symbol={} from={} to={} availableDays={} expectedDays={} reason={}",
                symbol,
                coverage.from(),
                coverage.to(),
                coverage.availableDays(),
                coverage.expectedDays(),
                reason);
        warmupExecutor.execute(() -> {
            try {
                warmupSymbolSync(symbol, coverage.from(), coverage.to(), reason);
            } finally {
                inFlightWarmups.remove(warmupKey);
            }
        });
    }

    public void warmupSymbolSync(String rawSymbol, LocalDate requestedFrom, LocalDate requestedTo, String reason) {
        String symbol = normalizeSymbol(rawSymbol);
        LocalDate from = requestedFrom;
        LocalDate to = effectiveTo(requestedTo);
        if (!warmupProperties.isEnabled() || symbol == null || from == null || to == null || to.isBefore(from)) {
            return;
        }
        MetalHistoryCoverage coverage = getCoverage(symbol, from, to);
        if (coverage.ready()) {
            log.debug("[METAL_HISTORY_WARMUP] already_ready symbol={} from={} to={}", symbol, from, to);
            return;
        }
        if (GRAM_GOLD.equals(symbol)) {
            MetalPriceIngestService.IngestSummary summary = metalPriceIngestService.backfillRange(from, to, reason);
            log.info(
                    "[METAL_HISTORY_WARMUP] completed symbol={} from={} to={} inserted={} reason={}",
                    symbol,
                    summary.from(),
                    summary.to(),
                    summary.insertedRows(),
                    reason);
            return;
        }
        PreciousMetalUsdCatalog.Entry entry = PreciousMetalUsdCatalog.byCanonicalOrNull(symbol);
        if (entry == null) {
            return;
        }
        LocalDate chunkStart = from;
        int chunkDays = Math.max(1, warmupProperties.getMaxRangeChunkDays());
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = min(chunkStart.plusDays(chunkDays - 1L), to);
            IsYatirimMetalUsdIngestService.IngestSummary summary =
                    isYatirimMetalUsdIngestService.ingestRange(entry, chunkStart, chunkEnd, false);
            log.info(
                    "[METAL_HISTORY_WARMUP] chunk_done symbol={} from={} to={} inserted={} skippedDuplicate={} ignoredOutOfRange={} reason={}",
                    symbol,
                    chunkStart,
                    chunkEnd,
                    summary.inserted(),
                    summary.skippedDuplicate(),
                    summary.ignoredOutOfRange(),
                    reason);
            chunkStart = chunkEnd.plusDays(1);
            if (!chunkStart.isAfter(to)) {
                sleepQuietly(Math.max(0L, warmupProperties.getDelayBetweenChunksMs()));
            }
        }
    }

    private Optional<LocalDate> oldestDay(String symbol) {
        if (isUsdOunceSymbol(symbol)) {
            return repository.findTopBySymbolAndSourceOrderByTimestampAsc(symbol, PreciousMetalUsdCatalog.SOURCE)
                    .map(row -> row.getTimestamp().toLocalDate());
        }
        return repository.findTopBySymbolOrderByTimestampAsc(symbol)
                .map(row -> row.getTimestamp().toLocalDate());
    }

    private Optional<LocalDate> newestDay(String symbol) {
        if (isUsdOunceSymbol(symbol)) {
            return repository.findTopBySymbolAndSourceOrderByTimestampDesc(symbol, PreciousMetalUsdCatalog.SOURCE)
                    .map(row -> row.getTimestamp().toLocalDate());
        }
        return repository.findTopBySymbolOrderByTimestampDesc(symbol)
                .map(row -> row.getTimestamp().toLocalDate());
    }

    private String normalizeSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return null;
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (GRAM_GOLD.equals(symbol) || GRAM_GOLD_ALIAS.equals(symbol)) {
            return GRAM_GOLD;
        }
        return PreciousMetalUsdCatalog.isUsdOunceMetal(symbol) ? symbol : null;
    }

    private static boolean isUsdOunceSymbol(String symbol) {
        return symbol != null && PreciousMetalUsdCatalog.isUsdOunceMetal(symbol);
    }

    private static LocalDate effectiveTo(LocalDate requestedTo) {
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        if (requestedTo == null || requestedTo.isAfter(today)) {
            return today;
        }
        return requestedTo;
    }

    private static LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private static String warmupKey(String symbol, LocalDate from, LocalDate to) {
        return symbol + "|" + from + "|" + to;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdownExecutor() {
        warmupExecutor.shutdownNow();
    }
}
