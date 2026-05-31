package com.nurseli.marketdata.application;

import com.nurseli.marketdata.application.crypto.CryptoDailyOhlcUtil;
import com.nurseli.marketdata.config.CryptoHistoryBackfillProperties;
import com.nurseli.marketdata.domain.price.CryptoSymbolMapping;
import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoClient;
import com.nurseli.marketdata.infrastructure.persistence.CryptoDailyCandleRepository;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoPriceIngestService {

    private static final ZoneId MARKET_WALL_CLOCK_ZONE = ZoneId.of("Europe/Istanbul");
    private static final int INCREMENTAL_OHLC_DAYS = 90;
    private static final int REPAIR_OHLC_DAYS = 365;

    private final CoinGeckoClient coinGeckoClient;
    private final MarketPriceHistoryRepository repository;
    private final CryptoDailyCandleRepository cryptoDailyCandleRepository;
    private final CryptoHistoryBackfillProperties cryptoHistoryBackfillProperties;

    @Transactional
    @CacheEvict(
            cacheNames = {"market:batch", "market:indicators"},
            allEntries = true
    )
    public void fetchAndSaveCryptoPrices() {

        String ids = CryptoSymbolMapping.idsAsCsv();

        Map<String, Map<String, Double>> response =
                coinGeckoClient.fetchPrices(ids);

        if (response.isEmpty()) {
            log.warn("[CRYPTO] No data received from CoinGecko");
            return;
        }

        response.forEach((coinId, priceMap) -> {

            Number usdNumber = priceMap.get("usd");
            if (usdNumber == null) return;

            BigDecimal usdPrice = BigDecimal.valueOf(usdNumber.doubleValue());

            String symbol = CryptoSymbolMapping.SYMBOL_TO_ID.entrySet()
                    .stream()
                    .filter(e -> e.getValue().equals(coinId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);

            if (symbol == null) return;

            MarketPriceHistory entity = new MarketPriceHistory();

            entity.setSymbol(symbol);
            entity.setBuyPrice(SpreadCalculator.buyPrice(usdPrice));
            entity.setSellPrice(SpreadCalculator.sellPrice(usdPrice));
            entity.setSource("COINGECKO");
            entity.setTimestamp(LocalDateTime.now(MARKET_WALL_CLOCK_ZONE));

            repository.save(entity);
            log.info("[CRYPTO] Saved {}", symbol);
        });
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveHistoryBackfill(int periodDays) {
        int days = Math.max(7, Math.min(periodDays, 365));
        long delayMs = Math.max(500L, cryptoHistoryBackfillProperties.getDelayMsBetweenCoins());
        int skipAt = cryptoHistoryBackfillProperties.getSkipSymbolIfCandleCountAtLeast();
        LocalDate rangeStart = LocalDate.now(MARKET_WALL_CLOCK_ZONE).minusDays(days);
        for (Map.Entry<String, String> entry : CryptoSymbolMapping.SYMBOL_TO_ID.entrySet()) {
            String symbol = entry.getKey();
            String coinId = entry.getValue();
            try {
                if (skipAt > 0) {
                    int threshold = Math.min(skipAt, days);
                    long have = cryptoDailyCandleRepository.countBySymbolAndAsOfGreaterThanEqual(symbol, rangeStart);
                    if (have >= threshold) {
                        log.info("[CRYPTO_HISTORY] skip warm symbol={} candlesInRange={} threshold={}", symbol, have, threshold);
                        sleepQuietly(delayMs);
                        continue;
                    }
                }
                List<CoinGeckoClient.OhlcPoint> points = coinGeckoClient.fetchDailyOhlc(coinId, days);
                int changed = upsertDailyOhlcPoints(symbol, points, LocalDate.now(MARKET_WALL_CLOCK_ZONE), "COINGECKO_OHLC");
                log.info("[CRYPTO_HISTORY] symbol={} coinId={} points={} changed={}", symbol, coinId, points.size(), changed);
                sleepQuietly(delayMs);
            } catch (Exception ex) {
                log.warn("[CRYPTO_HISTORY] failed symbol={} coinId={} reason={}", symbol, coinId, ex.getMessage());
                sleepQuietly(delayMs);
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void fetchAndSaveIncrementalDailyHistory() {
        for (String symbol : CryptoSymbolMapping.SYMBOL_TO_ID.keySet()) {
            try {
                ingestIncrementalForSymbol(symbol);
            } catch (Exception ex) {
                log.debug("[CRYPTO_HISTORY] incremental failed symbol={} reason={}", symbol, ex.getMessage());
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void ensureHistoryCoverage(String symbol, LocalDate requestedFrom, LocalDate requestedTo) {
        if (symbol == null || symbol.isBlank() || requestedFrom == null || requestedTo == null) {
            return;
        }
        String sym = normalizeSymbol(symbol);
        if (sym == null) {
            return;
        }
        String coinId = CryptoSymbolMapping.SYMBOL_TO_ID.get(sym);
        if (coinId == null) {
            return;
        }
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        LocalDate to = requestedTo.isAfter(today) ? today : requestedTo;
        if (requestedFrom.isAfter(to)) {
            return;
        }
        Optional<CryptoDailyCandle> oldest = cryptoDailyCandleRepository.findTopBySymbolOrderByAsOfAsc(sym);
        if (oldest.isPresent() && !oldest.get().getAsOf().isAfter(requestedFrom)) {
            return;
        }

        int requestedDays = Math.toIntExact(Math.max(1, ChronoUnit.DAYS.between(requestedFrom, to) + 2));
        List<CoinGeckoClient.OhlcPoint> points = List.of();
        if (requestedDays <= 365) {
            points = coinGeckoClient.fetchDailyOhlc(coinId, requestedDays);
        }
        if (points.isEmpty()) {
            points = coinGeckoClient.fetchDailyMarketChart(coinId, requestedDays);
        }
        int changed = upsertDailyOhlcPoints(sym, points, to, "COINGECKO_OHLC");
        log.info(
                "[CRYPTO_HISTORY] ensure_coverage symbol={} from={} to={} points={} changed={}",
                sym, requestedFrom, to, points.size(), changed
        );
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void ingestIncrementalForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }
        String sym = normalizeSymbol(symbol);
        String coinId = sym == null ? null : CryptoSymbolMapping.SYMBOL_TO_ID.get(sym);
        if (coinId == null) {
            return;
        }
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        try {
            List<CoinGeckoClient.OhlcPoint> points = coinGeckoClient.fetchDailyOhlc(coinId, INCREMENTAL_OHLC_DAYS);
            upsertDailyOhlcPoints(sym, points, today, "COINGECKO_OHLC");
            sleepQuietly(800);
        } catch (Exception ex) {
            log.debug("[CRYPTO_HISTORY] incremental symbol={} reason={}", sym, ex.getMessage());
            sleepQuietly(800);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public void repairFlatDailyCandles() {
        long delayMs = Math.max(500L, cryptoHistoryBackfillProperties.getDelayMsBetweenCoins());
        LocalDate today = LocalDate.now(MARKET_WALL_CLOCK_ZONE);
        for (Map.Entry<String, String> entry : CryptoSymbolMapping.SYMBOL_TO_ID.entrySet()) {
            String symbol = entry.getKey();
            String coinId = entry.getValue();
            try {
                List<CoinGeckoClient.OhlcPoint> points = coinGeckoClient.fetchDailyOhlc(coinId, REPAIR_OHLC_DAYS);
                int changed = upsertDailyOhlcPoints(symbol, points, today, "COINGECKO_OHLC");
                log.info("[CRYPTO_HISTORY] repair_flat symbol={} coinId={} points={} changed={}", symbol, coinId, points.size(), changed);
                sleepQuietly(delayMs);
            } catch (Exception ex) {
                log.warn("[CRYPTO_HISTORY] repair_flat failed symbol={} coinId={} reason={}", symbol, coinId, ex.getMessage());
                sleepQuietly(delayMs);
            }
        }
    }

    @Transactional
    @CacheEvict(cacheNames = {"market:batch", "market:indicators"}, allEntries = true)
    public int upsertDailyOhlcPoints(
            String symbol,
            List<CoinGeckoClient.OhlcPoint> rawPoints,
            LocalDate today,
            String source
    ) {
        if (symbol == null || symbol.isBlank() || rawPoints == null || rawPoints.isEmpty()) {
            return 0;
        }
        List<CoinGeckoClient.OhlcPoint> points = CryptoDailyOhlcUtil.aggregateToDaily(rawPoints);
        int changed = 0;
        for (CoinGeckoClient.OhlcPoint point : points) {
            if (point == null || point.day() == null || point.day().isAfter(today)) {
                continue;
            }
            Optional<CryptoDailyCandle> existingOpt = cryptoDailyCandleRepository.findBySymbolAndAsOf(symbol, point.day());
            if (existingOpt.isEmpty()) {
                cryptoDailyCandleRepository.save(newCandle(symbol, point, source));
                changed++;
                continue;
            }
            CryptoDailyCandle existing = existingOpt.get();
            if (CryptoDailyOhlcUtil.isFlat(existing) && !CryptoDailyOhlcUtil.isFlat(point)) {
                applyOhlc(existing, point, source);
                cryptoDailyCandleRepository.save(existing);
                changed++;
            }
        }
        return changed;
    }

    private String normalizeSymbol(String symbol) {
        String sym = symbol.trim().toUpperCase();
        String coinId = CryptoSymbolMapping.SYMBOL_TO_ID.get(sym);
        if (coinId != null) {
            return sym;
        }
        String alt = sym.endsWith("USDT") ? sym : sym + "USDT";
        return CryptoSymbolMapping.SYMBOL_TO_ID.containsKey(alt) ? alt : null;
    }

    private static CryptoDailyCandle newCandle(String symbol, CoinGeckoClient.OhlcPoint point, String source) {
        CryptoDailyCandle candle = new CryptoDailyCandle();
        candle.setSymbol(symbol);
        candle.setAsOf(point.day());
        applyOhlc(candle, point, source);
        return candle;
    }

    private static void applyOhlc(CryptoDailyCandle candle, CoinGeckoClient.OhlcPoint point, String source) {
        candle.setOpenPrice(point.open());
        candle.setHighPrice(point.high());
        candle.setLowPrice(point.low());
        candle.setClosePrice(point.close());
        candle.setVolume(point.volume());
        candle.setSource(source);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
