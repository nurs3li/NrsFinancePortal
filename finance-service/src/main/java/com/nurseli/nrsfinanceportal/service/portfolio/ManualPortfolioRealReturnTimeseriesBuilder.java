package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioTimeseriesPointDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.service.portfolio.HistoricalManualPriceResolverService.ManualChartPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Günlük reel K/Z: açık pozisyonların piyasa değeri − TÜFE ile taşınmış maliyet (nominal zaman serisi ile aynı ızgara).
 * {@code openCostBasisTry} alanı enflasyona göre düzeltilmiş maliyet toplamını taşır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManualPortfolioRealReturnTimeseriesBuilder {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");
    private static final int MANUAL_TS_MAX_POINTS = 400;

    private final HistoricalManualPriceResolverService priceResolver;

    public List<ManualPortfolioTimeseriesPointDto> build(
            List<ManualPortfolioPosition> axisScope,
            List<ManualPortfolioPosition> valueScope,
            CpiIndexLookup cpiLookup,
            LocalDate from,
            LocalDate to
    ) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        if (axisScope.isEmpty() || valueScope.isEmpty()) {
            return List.of();
        }
        if (cpiLookup == null || !cpiLookup.isAvailable()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(TZ);
        LocalDate end = to.isAfter(today) ? today : to;
        LocalDate earliestBuy = axisScope.stream()
                .map(ManualPortfolioPosition::getBuyDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(end);
        LocalDate effectiveFrom = from.isBefore(earliestBuy) ? earliestBuy : from;
        if (effectiveFrom.isAfter(end)) {
            return List.of();
        }

        record SymKey(AssetType type, String symbol) {}
        Set<SymKey> keys = new HashSet<>();
        for (ManualPortfolioPosition p : valueScope) {
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            if (overlapsTimeseriesWindow(p, effectiveFrom, end)) {
                keys.add(new SymKey(p.getType(), p.getSymbol().trim()));
            }
        }
        Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees = new HashMap<>();
        LocalDate histFrom = effectiveFrom.minusDays(14);
        for (SymKey k : keys) {
            TreeMap<LocalDate, BigDecimal> tree = new TreeMap<>();
            try {
                List<ManualChartPoint> pts = priceResolver.loadDailyCloseSeriesTry(k.type(), k.symbol(), histFrom, end);
                for (ManualChartPoint pt : pts) {
                    if (pt.priceTry() != null && pt.priceTry().signum() > 0) {
                        tree.put(pt.date(), pt.priceTry());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("[MANUAL_TS_REAL] price series failed type={} symbol={}: {}", k.type(), k.symbol(), ex.toString());
            }
            priceTrees.put(priceTreeKey(k.type(), k.symbol()), tree);
        }

        long spanDays = ChronoUnit.DAYS.between(effectiveFrom, end) + 1;
        int step = (int) Math.max(1, Math.ceil(spanDays / (double) MANUAL_TS_MAX_POINTS));

        List<ManualPortfolioTimeseriesPointDto> out = new ArrayList<>();
        LocalDate d = effectiveFrom;
        while (!d.isAfter(end)) {
            BigDecimal inflationCost = inflationAdjustedOpenCostTry(valueScope, cpiLookup, d);
            BigDecimal mval = portfolioMarketValueTry(valueScope, priceTrees, d);
            if (inflationCost != null && mval != null) {
                out.add(new ManualPortfolioTimeseriesPointDto(
                        d,
                        inflationCost.setScale(8, RoundingMode.HALF_UP),
                        mval.setScale(8, RoundingMode.HALF_UP)
                ));
            }
            d = d.plusDays(step);
        }

        LocalDate lastSample = out.isEmpty() ? null : out.get(out.size() - 1).date();
        if (lastSample != null && lastSample.isBefore(end)) {
            BigDecimal inflationCost = inflationAdjustedOpenCostTry(valueScope, cpiLookup, end);
            BigDecimal mval = portfolioMarketValueTry(valueScope, priceTrees, end);
            if (inflationCost != null && mval != null) {
                out.add(new ManualPortfolioTimeseriesPointDto(
                        end,
                        inflationCost.setScale(8, RoundingMode.HALF_UP),
                        mval.setScale(8, RoundingMode.HALF_UP)
                ));
            }
        }
        return out;
    }

    private static BigDecimal inflationAdjustedOpenCostTry(
            List<ManualPortfolioPosition> positions,
            CpiIndexLookup cpi,
            LocalDate d
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean anyOpen = false;
        for (ManualPortfolioPosition p : positions) {
            if (!isOpenOnDateForTs(p, d)) {
                continue;
            }
            if (p.getBuyDate() == null) {
                return null;
            }
            anyOpen = true;
            Optional<BigDecimal> buyCpi = cpi.indexAtOrBefore(p.getBuyDate());
            Optional<BigDecimal> endCpi = cpi.indexAtOrBefore(d);
            if (buyCpi.isEmpty() || endCpi.isEmpty()) {
                return null;
            }
            BigDecimal buyCost = buyCostBasisFromPosition(p);
            BigDecimal factor = endCpi.get().divide(buyCpi.get(), 8, RoundingMode.HALF_UP);
            sum = sum.add(buyCost.multiply(factor).setScale(8, RoundingMode.HALF_UP));
        }
        return anyOpen ? sum : BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
    }

    private static BigDecimal portfolioMarketValueTry(
            List<ManualPortfolioPosition> positions,
            Map<String, NavigableMap<LocalDate, BigDecimal>> priceTrees,
            LocalDate d
    ) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean anyOpen = false;
        for (ManualPortfolioPosition p : positions) {
            if (!isOpenOnDateForTs(p, d)) {
                continue;
            }
            if (p.getType() == null || p.getSymbol() == null || p.getSymbol().isBlank()) {
                continue;
            }
            anyOpen = true;
            NavigableMap<LocalDate, BigDecimal> tree = priceTrees.get(priceTreeKey(p.getType(), p.getSymbol().trim()));
            if (tree == null || tree.isEmpty()) {
                return null;
            }
            Map.Entry<LocalDate, BigDecimal> e = tree.floorEntry(d);
            if (e == null || e.getValue() == null || e.getValue().signum() <= 0) {
                return null;
            }
            BigDecimal q = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
            sum = sum.add(e.getValue().multiply(q));
        }
        return anyOpen ? sum : BigDecimal.ZERO;
    }

    private static BigDecimal buyCostBasisFromPosition(ManualPortfolioPosition p) {
        BigDecimal qty = p.getQuantity() == null ? BigDecimal.ZERO : p.getQuantity();
        BigDecimal buyPx = p.getBuyPrice() == null ? BigDecimal.ZERO : p.getBuyPrice();
        BigDecimal buyFee = p.getBuyFee();
        if (buyFee == null || buyFee.signum() < 0) {
            buyFee = BigDecimal.ZERO;
        }
        return buyPx.multiply(qty).add(buyFee).setScale(8, RoundingMode.HALF_UP);
    }

    private static boolean overlapsTimeseriesWindow(ManualPortfolioPosition p, LocalDate from, LocalDate to) {
        if (p.getBuyDate() == null || p.getBuyDate().isAfter(to)) {
            return false;
        }
        return p.getStatus() != ManualPositionStatus.SOLD
                || p.getSellDate() == null
                || !p.getSellDate().isBefore(from);
    }

    private static boolean isOpenOnDateForTs(ManualPortfolioPosition p, LocalDate d) {
        if (p.getBuyDate() == null || d.isBefore(p.getBuyDate())) {
            return false;
        }
        if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null) {
            return d.isBefore(p.getSellDate());
        }
        return true;
    }

    private static String priceTreeKey(AssetType type, String symbol) {
        return type.name() + "|" + symbol;
    }
}
