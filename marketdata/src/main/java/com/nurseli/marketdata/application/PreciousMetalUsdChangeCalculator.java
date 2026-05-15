package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.PreciousMetalUsdChanges;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import com.nurseli.marketdata.repository.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PreciousMetalUsdChangeCalculator {

    private static final int SCALE = 2;

    private final MarketPriceHistoryRepository repository;

    public PreciousMetalUsdChanges compute(String canonicalSymbol) {
        Optional<MarketPriceHistory> latestOpt = repository.findTopBySymbolAndSourceOrderByTimestampDesc(
                canonicalSymbol,
                PreciousMetalUsdCatalog.SOURCE);
        if (latestOpt.isEmpty()) {
            return new PreciousMetalUsdChanges(null, null, null, null);
        }
        MarketPriceHistory latest = latestOpt.get();
        BigDecimal latestPx = mid(latest);
        if (latestPx == null || latestPx.signum() <= 0) {
            return new PreciousMetalUsdChanges(null, null, null, null);
        }
        LocalDate latestDate = latest.getTimestamp().toLocalDate();
        LocalDateTime rangeStart = latest.getTimestamp().minusYears(2).minusDays(14);
        List<MarketPriceHistory> asc = repository.findBySymbolAndSourceAndTimestampRange(
                canonicalSymbol,
                PreciousMetalUsdCatalog.SOURCE,
                rangeStart,
                latest.getTimestamp().plusDays(2));
        if (asc.isEmpty()) {
            return new PreciousMetalUsdChanges(null, null, null, null);
        }

        MarketPriceHistory dailyBase = asc.size() >= 2 ? asc.get(asc.size() - 2) : null;
        MarketPriceHistory weeklyBase = findBaseOnOrBefore(asc, latestDate.minusDays(7));
        MarketPriceHistory monthlyBase = findBaseOnOrBefore(asc, latestDate.minusMonths(1));
        MarketPriceHistory yearlyBase = findBaseOnOrBefore(asc, latestDate.minusYears(1));

        return new PreciousMetalUsdChanges(
                pct(latestPx, dailyBase),
                pct(latestPx, weeklyBase),
                pct(latestPx, monthlyBase),
                pct(latestPx, yearlyBase));
    }

    private static MarketPriceHistory findBaseOnOrBefore(List<MarketPriceHistory> asc, LocalDate target) {
        MarketPriceHistory best = null;
        for (MarketPriceHistory r : asc) {
            LocalDate d = r.getTimestamp().toLocalDate();
            if (!d.isAfter(target)) {
                best = r;
            }
        }
        return best;
    }

    private static BigDecimal pct(BigDecimal latest, MarketPriceHistory baseRow) {
        if (baseRow == null) {
            return null;
        }
        BigDecimal b = mid(baseRow);
        if (b == null || b.signum() <= 0) {
            return null;
        }
        return latest.subtract(b)
                .divide(b, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal mid(MarketPriceHistory r) {
        if (r.getBuyPrice() == null || r.getSellPrice() == null) {
            return r.getBuyPrice() != null ? r.getBuyPrice() : r.getSellPrice();
        }
        return r.getBuyPrice().add(r.getSellPrice()).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }
}
