package com.nurseli.marketdata.application.ingest;

import com.nurseli.marketdata.application.SpreadCalculator;
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
import java.time.temporal.ChronoUnit;
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

    public record IngestSummary(
            int insertedRows,
            LocalDate from,
            LocalDate to
    ) {}

    @Transactional
    @CacheEvict(
            cacheNames = {"market:batch", "market:indicators"},
            allEntries = true
    )
    public void ensureHistoricalBackfill(int days) {
        int safeDays = Math.max(30, Math.min(days, 3650));
        LocalDate today = LocalDate.now();
        backfillRange(today.minusDays(safeDays - 1L), today, "legacy-days");
    }

    @Transactional
    @CacheEvict(
            cacheNames = {"market:batch", "market:indicators"},
            allEntries = true
    )
    public IngestSummary backfillRange(LocalDate fromInclusive, LocalDate toInclusive, String reason) {
        LocalDate today = LocalDate.now();
        LocalDate from = fromInclusive;
        LocalDate to = toInclusive != null && !toInclusive.isAfter(today) ? toInclusive : today;
        if (from == null || to == null || to.isBefore(from)) {
            return new IngestSummary(0, fromInclusive, toInclusive);
        }

        List<CoinGeckoMetalClient.DailyGoldTryPoint> points = client.fetchGoldTryHistoryPerOunceRange(from, to);
        if (points.isEmpty()) {
            long spanDays = ChronoUnit.DAYS.between(from, to) + 1;
            if (spanDays > 0 && spanDays <= 3650) {
                points = client.fetchGoldTryHistoryPerOunce((int) spanDays);
            }
        }

        int saved = 0;
        for (CoinGeckoMetalClient.DailyGoldTryPoint p : points) {
            if (p.date() == null || p.date().isBefore(from) || p.date().isAfter(to)) {
                continue;
            }
            LocalDateTime dayTs = p.date().atStartOfDay();
            if (repository.existsForDay(SYMBOL, dayTs, dayTs.plusDays(1))) {
                continue;
            }
            BigDecimal gramPrice = p.ounceTry().divide(OUNCE_TO_GRAM, 2, RoundingMode.HALF_UP);
            if (gramPrice.signum() <= 0) {
                continue;
            }
            MarketPriceHistory row = new MarketPriceHistory();
            row.setSymbol(SYMBOL);
            row.setBuyPrice(SpreadCalculator.buyPrice(gramPrice));
            row.setSellPrice(SpreadCalculator.sellPrice(gramPrice));
            row.setSource("COINGECKO_RANGE");
            row.setTimestamp(dayTs);
            repository.save(row);
            saved++;
        }

        if (saved > 0) {
            log.info(
                    "[METAL] Backfilled XAU_TRY rows={} from={} to={} reason={}",
                    saved,
                    from,
                    to,
                    reason == null || reason.isBlank() ? "unspecified" : reason);
        }
        return new IngestSummary(saved, from, to);
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