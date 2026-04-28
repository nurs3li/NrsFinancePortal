package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.SimulationResponseDto;
import com.nurseli.nrsfinanceportal.common.dto.SimulationPerformancePointDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.pricing.SymbolNormalizer;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.dto.MarketPriceHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private final MarketDataClient marketDataClient;
    private final DateToDaysHelper dateToDaysHelper;

    @Transactional(readOnly = true)
    public SimulationResponseDto simulate(
            AssetType type,
            String rawSymbol,
            BigDecimal amountTry,
            LocalDate buyDate,
            BigDecimal manualBuyPriceTry
    ) {
        if (type == AssetType.STOCK) {
            throw new IllegalArgumentException(
                    "STOCK simulation is not supported yet. Historical stock data feed is planned in the next phase."
            );
        }

        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("Symbol is required");
        }
        if (amountTry == null || amountTry.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (buyDate == null) {
            throw new IllegalArgumentException("Buy date is required");
        }
        if (!buyDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Buy date must be in the past");
        }

        String symbol = SymbolNormalizer.normalize(type, rawSymbol.trim().toUpperCase());
        int days = dateToDaysHelper.toDays(buyDate);

        List<MarketPriceHistoryDto> history = marketDataClient.getHistory(type, symbol, days);
        if (history == null || history.isEmpty()) {
            throw new IllegalStateException("No historical data for symbol: " + symbol);
        }

        HistoricalPriceRef historicalRef;
        if (manualBuyPriceTry != null && manualBuyPriceTry.signum() > 0) {
            historicalRef = new HistoricalPriceRef(
                    manualBuyPriceTry,
                    buyDate,
                    "USER_INPUT",
                    "EXACT"
            );
        } else {
            historicalRef = resolveHistoricalPriceAtOrBeforeDate(history, buyDate);
        }
        BigDecimal historicalPrice = historicalRef.priceTry();

        BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, symbol));
        if (currentPrice.signum() <= 0) {
            throw new IllegalStateException("Current price not found");
        }

        BigDecimal units = amountTry.divide(historicalPrice, 8, RoundingMode.HALF_UP);
        BigDecimal currentValue = units.multiply(currentPrice);
        BigDecimal pnl = currentValue.subtract(amountTry);

        BigDecimal pnlPct = amountTry.signum() == 0
                ? BigDecimal.ZERO
                : pnl.divide(amountTry, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        String message = "%s tarihinde %s TRY %s yatırımı bugün %s TRY olurdu. (alış: %s, kaynak: %s)"
                .formatted(
                        buyDate,
                        amountTry.stripTrailingZeros().toPlainString(),
                        symbol,
                        currentValue.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        historicalRef.priceDate(),
                        historicalRef.source()
                );

        List<SimulationPerformancePointDto> performanceSeries = buildPerformanceSeries(
                history,
                buyDate,
                historicalPrice,
                currentPrice
        );

        return new SimulationResponseDto(
                type.name(),
                symbol,
                buyDate,
                amountTry,
                historicalPrice,
                currentPrice,
                units,
                currentValue,
                pnl,
                pnlPct,
                historicalRef.source(),
                historicalRef.priceDate(),
                historicalRef.qualityFlag(),
                performanceSeries,
                message
        );
    }

    /**
     * "Nearest" yerine güvenli kural:
     * - Önce buyDate'e eşit kayıt
     * - Yoksa buyDate'den önceki en yakın kayıt (forward lookup yok)
     */
    private HistoricalPriceRef resolveHistoricalPriceAtOrBeforeDate(
            List<MarketPriceHistoryDto> history,
            LocalDate buyDate
    ) {
        MarketPriceHistoryDto row = history.stream()
                .filter(h -> h.timestamp() != null)
                .filter(h -> !h.timestamp().toLocalDate().isAfter(buyDate))
                .max(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .orElseThrow(() -> new IllegalStateException("No historical point at or before selected date"));

        BigDecimal price = midPrice(row);
        if (price == null || price.signum() <= 0) {
            throw new IllegalStateException("Historical price is invalid");
        }
        String quality = row.timestamp().toLocalDate().isEqual(buyDate) ? "EXACT" : "PREVIOUS_DAY";
        return new HistoricalPriceRef(price, row.timestamp().toLocalDate(), "SYSTEM_HISTORY", quality);
    }

    /**
     * MarketDashboardService'teki geçmiş veri yaklaşımıyla uyumlu:
     * geçmiş fiyat noktalarını tarihe göre sıralar ve alış fiyatına göre kümülatif getiri (%) üretir.
     */
    private List<SimulationPerformancePointDto> buildPerformanceSeries(
            List<MarketPriceHistoryDto> history,
            LocalDate buyDate,
            BigDecimal buyPriceTry,
            BigDecimal currentPriceTry
    ) {
        BigDecimal safeBuy = nz(buyPriceTry);
        if (safeBuy.signum() <= 0) {
            return List.of();
        }

        List<SimulationPerformancePointDto> points = history.stream()
                .filter(Objects::nonNull)
                .filter(h -> h.timestamp() != null)
                .filter(h -> !h.timestamp().toLocalDate().isBefore(buyDate))
                .sorted(Comparator.comparing(MarketPriceHistoryDto::timestamp))
                .map(h -> toPoint(h.timestamp(), midPrice(h), safeBuy))
                .filter(Objects::nonNull)
                .toList();

        LocalDate today = LocalDate.now();
        BigDecimal todayPct = currentPriceTry.subtract(safeBuy)
                .divide(safeBuy, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        SimulationPerformancePointDto todayPoint =
                new SimulationPerformancePointDto(today, currentPriceTry, todayPct);

        if (points.isEmpty()) {
            return List.of(
                    new SimulationPerformancePointDto(buyDate, safeBuy, BigDecimal.ZERO),
                    todayPoint
            );
        }

        SimulationPerformancePointDto last = points.get(points.size() - 1);
        if (!today.isAfter(last.date())) {
            return points;
        }

        return java.util.stream.Stream.concat(points.stream(), java.util.stream.Stream.of(todayPoint)).toList();
    }

    private SimulationPerformancePointDto toPoint(LocalDateTime ts, BigDecimal priceTry, BigDecimal buyPriceTry) {
        if (priceTry == null || priceTry.signum() <= 0) {
            return null;
        }
        BigDecimal pct = priceTry.subtract(buyPriceTry)
                .divide(buyPriceTry, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        return new SimulationPerformancePointDto(ts.toLocalDate(), priceTry, pct);
    }

    private BigDecimal midPrice(MarketPriceHistoryDto row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal buy = row.buyPrice();
        BigDecimal sell = row.sellPrice();
        if (buy != null && buy.signum() > 0 && sell != null && sell.signum() > 0) {
            return buy.add(sell).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        }
        if (buy != null && buy.signum() > 0) {
            return buy;
        }
        if (sell != null && sell.signum() > 0) {
            return sell;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private record HistoricalPriceRef(
            BigDecimal priceTry,
            LocalDate priceDate,
            String source,
            String qualityFlag
    ) {
    }
}