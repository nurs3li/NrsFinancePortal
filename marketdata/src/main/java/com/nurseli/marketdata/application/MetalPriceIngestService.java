package com.nurseli.marketdata.application;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.infrastructure.coingecko.CoinGeckoMetalClient;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetalPriceIngestService {

    private static final BigDecimal OUNCE_TO_GRAM =
            new BigDecimal("31.1034768");
    private static final String SYMBOL = "XAU_TRY";

    private final CoinGeckoMetalClient client;
    private final MarketPriceHistoryRepository repository;

    @Transactional
    @CacheEvict(
            cacheNames = {"market:batch", "market:indicators"},
            allEntries = true
    )
    public void ensureHistoricalBackfill(int days) {
        int safeDays = Math.max(30, Math.min(days, 365));
        LocalDate needFrom = LocalDate.now().minusDays(safeDays - 1L);

        LocalDate oldestStored = repository.findTopBySymbolOrderByTimestampAsc(SYMBOL)
                .map(r -> r.getTimestamp().toLocalDate())
                .orElse(null);
        if (oldestStored != null && !oldestStored.isAfter(needFrom.plusDays(2))) {
            return;
        }

        List<CoinGeckoMetalClient.DailyGoldTryPoint> points = client.fetchGoldTryHistoryPerOunce(safeDays);
        int saved = 0;
        List<CoinGeckoMetalClient.DailyGoldTryOhlcPoint> ohlcPoints = client.fetchGoldTryOhlcHistoryPerOunce(safeDays);
        if (!ohlcPoints.isEmpty()) {
            for (CoinGeckoMetalClient.DailyGoldTryOhlcPoint p : ohlcPoints) {
                if (p.date() == null || p.date().isAfter(LocalDate.now().minusDays(1))) continue;
                if (p.date().isBefore(needFrom)) continue;
                LocalDateTime dayStart = p.date().atStartOfDay();
                LocalDateTime dayEnd = dayStart.plusDays(1);
                long dayCount = repository.countForDay(SYMBOL, dayStart, dayEnd);
                if (dayCount >= 4) continue;

                saved += saveOhlcAsIntradayPoints(
                        dayStart,
                        p.open().divide(OUNCE_TO_GRAM, 2, RoundingMode.HALF_UP),
                        p.high().divide(OUNCE_TO_GRAM, 2, RoundingMode.HALF_UP),
                        p.low().divide(OUNCE_TO_GRAM, 2, RoundingMode.HALF_UP),
                        p.close().divide(OUNCE_TO_GRAM, 2, RoundingMode.HALF_UP)
                );
            }
        } else if (!points.isEmpty()) {
            for (CoinGeckoMetalClient.DailyGoldTryPoint p : points) {
                if (p.date() == null || p.date().isAfter(LocalDate.now().minusDays(1))) continue;
                if (p.date().isBefore(needFrom)) continue;
                LocalDateTime dayTs = p.date().atStartOfDay();
                if (repository.existsForDay(SYMBOL, dayTs, dayTs.plusDays(1))) continue;

                BigDecimal gramPrice = p.ounceTry().divide(OUNCE_TO_GRAM, 2, RoundingMode.HALF_UP);
                MarketPriceHistory row = new MarketPriceHistory();
                row.setSymbol(SYMBOL);
                row.setBuyPrice(SpreadCalculator.buyPrice(gramPrice));
                row.setSellPrice(SpreadCalculator.sellPrice(gramPrice));
                row.setSource("COINGECKO");
                row.setTimestamp(dayTs);
                repository.save(row);
                saved++;
            }
        }
        if (saved > 0) {
            log.info("[METAL] Backfilled {} daily XAU_TRY points for last {} days", saved, safeDays);
        }
    }

    private int saveOhlcAsIntradayPoints(
            LocalDateTime dayStart,
            BigDecimal openGramTry,
            BigDecimal highGramTry,
            BigDecimal lowGramTry,
            BigDecimal closeGramTry
    ) {
        int saved = 0;
        LocalDateTime tOpen = dayStart;
        LocalDateTime tHigh = dayStart.plusHours(6);
        LocalDateTime tLow = dayStart.plusHours(12);
        LocalDateTime tClose = dayStart.plusHours(18);

        saved += saveIfMissing(tOpen, openGramTry);
        saved += saveIfMissing(tHigh, highGramTry);
        saved += saveIfMissing(tLow, lowGramTry);
        saved += saveIfMissing(tClose, closeGramTry);
        return saved;
    }

    private int saveIfMissing(LocalDateTime ts, BigDecimal gramPrice) {
        if (gramPrice == null || gramPrice.signum() <= 0) return 0;
        if (repository.existsBySymbolAndTimestamp(SYMBOL, ts)) return 0;
        MarketPriceHistory row = new MarketPriceHistory();
        row.setSymbol(SYMBOL);
        row.setBuyPrice(SpreadCalculator.buyPrice(gramPrice));
        row.setSellPrice(SpreadCalculator.sellPrice(gramPrice));
        row.setSource("COINGECKO");
        row.setTimestamp(ts);
        repository.save(row);
        return 1;
    }

    @Transactional
    @CacheEvict(
            cacheNames = {"market:batch", "market:indicators"},
            allEntries = true
    )
    public void fetchAndSaveGramGold() {

        BigDecimal ouncePriceTry = client.fetchGoldTryPerOunce();
        if (ouncePriceTry == null) {
            log.warn("[METAL] No price from CoinGecko (rate limit or error), skipping update.");
            return;
        }

        BigDecimal gramPrice =
                ouncePriceTry.divide(
                        OUNCE_TO_GRAM,
                        2,
                        RoundingMode.HALF_UP
                );

        MarketPriceHistory entity = new MarketPriceHistory();
        entity.setSymbol(SYMBOL);
        entity.setBuyPrice(SpreadCalculator.buyPrice(gramPrice));
        entity.setSellPrice(SpreadCalculator.sellPrice(gramPrice));
        entity.setSource("COINGECKO");
        entity.setTimestamp(LocalDateTime.now());

        repository.save(entity);

        log.info("[METAL] Saved GRAM GOLD = {} TRY", gramPrice);
    }
}