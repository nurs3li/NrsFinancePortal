package com.nurseli.marketdata.application.ingest;

import com.nurseli.marketdata.api.dto.CryptoHistoryCoverageResponse;
import com.nurseli.marketdata.api.dto.CryptoHistoryWarmupResponse;
import com.nurseli.marketdata.config.CryptoHistoryBackfillProperties;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.domain.price.CryptoSymbolMapping;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoClient;
import com.nurseli.marketdata.infrastructure.persistence.CryptoDailyCandleRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoHistoryWarmupService {

    private static final ZoneId MARKET_WALL_CLOCK_ZONE = ZoneId.of("Europe/Istanbul");
    private static final int MAX_RANGE_CHUNK_DAYS = 365;
    private static final long DELAY_BETWEEN_CHUNKS_MS = 350L;

    private final Set<String> inFlightWarmups = ConcurrentHashMap.newKeySet();
    private final ExecutorService warmupExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "crypto-history-warmup");
        thread.setDaemon(true);
        return thread;
    });

    private final CoinGeckoClient coinGeckoClient;
    private final CryptoDailyCandleRepository cryptoDailyCandleRepository;
    private final CryptoHistoryBackfillProperties backfillProperties;
    private final CryptoPriceIngestService cryptoPriceIngestService;

    public CryptoHistoryCoverageResponse getCoverage(String rawSymbol, LocalDate requestedFrom, LocalDate requestedTo) {
        String symbol = normalizeSymbol(rawSymbol);
        LocalDate to = effectiveTo(requestedTo);
        LocalDate from = requestedFrom;
        if (symbol == null || from == null || to == null || to.isBefore(from)) {
            return new CryptoHistoryCoverageResponse(
                    rawSymbol,
                    requestedFrom,
                    requestedTo,
                    null,
                    null,
                    0,
                    0,
                    false
            );
        }
        Optional<CryptoDailyCandle> oldest = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfAsc(symbol);
        Optional<CryptoDailyCandle> newest = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(symbol);
        long availableDays = cryptoDailyCandleRepository.countBySymbolAndAsOfBetween(symbol, from, to);
        long expectedDays = ChronoUnit.DAYS.between(from, to) + 1;
        boolean ready = oldest.map(CryptoDailyCandle::getAsOf).filter(day -> !day.isAfter(from)).isPresent()
                && newest.map(CryptoDailyCandle::getAsOf).filter(day -> !day.isBefore(to)).isPresent()
                && availableDays >= expectedDays;
        return new CryptoHistoryCoverageResponse(
                symbol,
                from,
                to,
                oldest.map(CryptoDailyCandle::getAsOf).orElse(null),
                newest.map(CryptoDailyCandle::getAsOf).orElse(null),
                availableDays,
                expectedDays,
                ready
        );
    }

    public CryptoHistoryWarmupResponse requestWarmup(
            List<String> rawSymbols,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            String reason
    ) {
        LocalDate to = effectiveTo(requestedTo);
        LocalDate from = requestedFrom;
        if (from == null || to == null || to.isBefore(from)) {
            return new CryptoHistoryWarmupResponse(
                    "INVALID_REQUEST",
                    0,
                    0,
                    requestedFrom,
                    requestedTo,
                    "from/to geçersiz"
            );
        }
        List<String> symbols = normalizeSymbols(rawSymbols);
        int queued = 0;
        int running = 0;
        int ready = 0;
        for (String symbol : symbols) {
            CryptoHistoryCoverageResponse coverage = getCoverage(symbol, from, to);
            if (coverage.ready()) {
                ready++;
                continue;
            }
            String warmupKey = warmupKey(symbol, from, to);
            if (!inFlightWarmups.add(warmupKey)) {
                running++;
                continue;
            }
            queued++;
            warmupExecutor.execute(() -> {
                try {
                    warmupSymbolSync(symbol, from, to, reason);
                } finally {
                    inFlightWarmups.remove(warmupKey);
                }
            });
        }
        String status = queued > 0 ? "QUEUED" : running > 0 ? "IN_FLIGHT" : ready == symbols.size() ? "READY" : "NOOP";
        String message = queued > 0
                ? "Kripto history warmup kuyruğa alındı."
                : running > 0
                ? "Kripto history warmup zaten çalışıyor."
                : ready == symbols.size()
                ? "Kripto history zaten hazır."
                : "Warmup başlatılamadı.";
        return new CryptoHistoryWarmupResponse(status, symbols.size(), queued, from, to, message);
    }

    public void warmSupportedSymbolsSync(int periodDays, String reason) {
        int days = Math.max(1, Math.min(periodDays, MAX_RANGE_CHUNK_DAYS));
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        LocalDate from = today.minusDays(days - 1L);
        for (String symbol : CryptoSymbolMapping.supportedSymbols().stream().sorted(Comparator.naturalOrder()).toList()) {
            warmupSymbolSync(symbol, from, today, reason);
        }
    }

    void warmupSymbolSync(String rawSymbol, LocalDate requestedFrom, LocalDate requestedTo, String reason) {
        String symbol = normalizeSymbol(rawSymbol);
        LocalDate to = effectiveTo(requestedTo);
        LocalDate from = requestedFrom;
        if (symbol == null || from == null || to == null || to.isBefore(from)) {
            return;
        }
        CryptoHistoryCoverageResponse coverage = getCoverage(symbol, from, to);
        if (coverage.ready()) {
            log.info(
                    "[CRYPTO_HISTORY_WARMUP] skip symbol={} coverage ready availableDays={} expectedDays={} reason={}",
                    symbol, coverage.availableDays(), coverage.expectedDays(), reason
            );
            return;
        }
        int skipAt = backfillProperties.getSkipSymbolIfCandleCountAtLeast();
        if (skipAt > 0) {
            long expectedDays = coverage.expectedDays();
            int threshold = expectedDays > 0
                    ? Math.min(skipAt, Math.toIntExact(expectedDays))
                    : skipAt;
            if (coverage.availableDays() >= threshold) {
                log.info(
                        "[CRYPTO_HISTORY_WARMUP] skip symbol={} candlesInRange={} threshold={} reason={}",
                        symbol, coverage.availableDays(), threshold, reason
                );
                return;
            }
        }
        Optional<CryptoDailyCandle> oldestBefore = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfAsc(symbol);
        if (oldestBefore.isEmpty()) {
            warmRange(symbol, from, to, reason);
            return;
        }
        LocalDate oldestDay = oldestBefore.get().getAsOf();
        if (oldestDay.isAfter(from)) {
            warmRange(symbol, from, oldestDay.minusDays(1), reason);
        }
        Optional<CryptoDailyCandle> newestAfterOlderFill = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfDesc(symbol);
        LocalDate newestDay = newestAfterOlderFill.map(CryptoDailyCandle::getAsOf).orElse(null);
        if (newestDay != null && newestDay.isBefore(to)) {
            warmRange(symbol, newestDay.plusDays(1), to, reason);
        }
    }

    private void warmRange(String symbol, LocalDate from, LocalDate to, String reason) {
        if (to.isBefore(from)) {
            return;
        }
        String coinId = CryptoSymbolMapping.SYMBOL_TO_ID.get(symbol);
        if (coinId == null) {
            return;
        }
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        LocalDate chunkStart = from;
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = min(chunkStart.plusDays(MAX_RANGE_CHUNK_DAYS - 1L), to);
            LocalDate ohlcWindowStart = today.minusDays(MAX_RANGE_CHUNK_DAYS - 1L);
            boolean useOhlcEndpoint = !chunkEnd.isBefore(ohlcWindowStart);
            List<CoinGeckoClient.OhlcPoint> points;
            if (useOhlcEndpoint) {
                int ohlcDays = Math.min(
                        MAX_RANGE_CHUNK_DAYS,
                        Math.toIntExact(ChronoUnit.DAYS.between(ohlcWindowStart, today) + 1)
                );
                final LocalDate filterFrom = chunkStart;
                final LocalDate filterTo = chunkEnd;
                points = coinGeckoClient.fetchDailyOhlc(coinId, Math.max(7, ohlcDays)).stream()
                        .filter(p -> p != null && p.day() != null
                                && !p.day().isBefore(filterFrom)
                                && !p.day().isAfter(filterTo))
                        .toList();
            } else {
                points = coinGeckoClient.fetchDailyMarketChartRange(coinId, chunkStart, chunkEnd);
            }
            int changed = cryptoPriceIngestService.upsertDailyOhlcPoints(
                    symbol,
                    points,
                    today,
                    useOhlcEndpoint ? "COINGECKO_OHLC" : "COINGECKO_RANGE"
            );
            log.info(
                    "[CRYPTO_HISTORY_WARMUP] symbol={} from={} to={} points={} changed={} reason={}",
                    symbol, chunkStart, chunkEnd, points.size(), changed, reason
            );
            chunkStart = chunkEnd.plusDays(1);
            if (!chunkStart.isAfter(to)) {
                sleepQuietly(DELAY_BETWEEN_CHUNKS_MS);
            }
        }
    }

    private List<String> normalizeSymbols(List<String> rawSymbols) {
        List<String> out = new ArrayList<>();
        if (rawSymbols == null || rawSymbols.isEmpty()) {
            out.addAll(CryptoSymbolMapping.supportedSymbols());
        } else {
            for (String rawSymbol : rawSymbols) {
                String normalized = normalizeSymbol(rawSymbol);
                if (normalized != null && !out.contains(normalized)) {
                    out.add(normalized);
                }
            }
        }
        return out;
    }

    private String normalizeSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return null;
        }
        String symbol = rawSymbol.trim().toUpperCase();
        if (CryptoSymbolMapping.SYMBOL_TO_ID.containsKey(symbol)) {
            return symbol;
        }
        String withUsdt = symbol.endsWith("USDT") ? symbol : symbol + "USDT";
        return CryptoSymbolMapping.SYMBOL_TO_ID.containsKey(withUsdt) ? withUsdt : null;
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
