package com.nurseli.marketdata.application;

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
        LocalDate rangeStart = LocalDate.now().minusDays(days);
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
                int inserted = 0;
                for (CoinGeckoClient.OhlcPoint p : points) {
                    if (cryptoDailyCandleRepository.existsBySymbolAndAsOf(symbol, p.day())) {
                        continue;
                    }
                    CryptoDailyCandle candle = new CryptoDailyCandle();
                    candle.setSymbol(symbol);
                    candle.setAsOf(p.day());
                    candle.setOpenPrice(p.open());
                    candle.setHighPrice(p.high());
                    candle.setLowPrice(p.low());
                    candle.setClosePrice(p.close());
                    candle.setVolume(p.volume());
                    candle.setSource("COINGECKO_OHLC");
                    cryptoDailyCandleRepository.save(candle);
                    inserted++;
                }
                log.info("[CRYPTO_HISTORY] symbol={} coinId={} points={} inserted={}", symbol, coinId, points.size(), inserted);
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
        List<CoinGeckoClient.OhlcPoint> points = coinGeckoClient.fetchDailyMarketChart(coinId, requestedDays);
        if (points.isEmpty() && requestedDays <= 365) {
            points = coinGeckoClient.fetchDailyOhlc(coinId, requestedDays);
        }
        int inserted = saveMissingDailyCandles(sym, points, to);
        log.info(
                "[CRYPTO_HISTORY] ensure_coverage symbol={} from={} to={} points={} inserted={}",
                sym, requestedFrom, to, points.size(), inserted
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
        LocalDate today = LocalDate.now();
        try {
            List<CoinGeckoClient.OhlcPoint> points = coinGeckoClient.fetchDailyOhlc(coinId, 14);
            saveMissingDailyCandles(sym, points, today);
            sleepQuietly(800);
        } catch (Exception ex) {
            log.debug("[CRYPTO_HISTORY] incremental symbol={} reason={}", sym, ex.getMessage());
            sleepQuietly(800);
        }
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

    private int saveMissingDailyCandles(String symbol, List<CoinGeckoClient.OhlcPoint> points, LocalDate today) {
        int inserted = 0;
        for (CoinGeckoClient.OhlcPoint p : points) {
            if (p == null || p.day() == null || p.day().isAfter(today)) {
                continue;
            }
            if (cryptoDailyCandleRepository.existsBySymbolAndAsOf(symbol, p.day())) {
                continue;
            }
            CryptoDailyCandle candle = new CryptoDailyCandle();
            candle.setSymbol(symbol);
            candle.setAsOf(p.day());
            candle.setOpenPrice(p.open());
            candle.setHighPrice(p.high());
            candle.setLowPrice(p.low());
            candle.setClosePrice(p.close());
            candle.setVolume(p.volume());
            candle.setSource("COINGECKO_OHLC");
            cryptoDailyCandleRepository.save(candle);
            inserted++;
        }
        return inserted;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}