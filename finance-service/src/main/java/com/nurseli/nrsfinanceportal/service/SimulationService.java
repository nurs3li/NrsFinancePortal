package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.SimulationResponseDto;
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
import java.util.Comparator;
import java.util.List;

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
            LocalDate buyDate
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

        MarketPriceHistoryDto nearest = history.stream()
                .min(Comparator.comparingLong(h ->
                        Math.abs(h.timestamp().toLocalDate().toEpochDay() - buyDate.toEpochDay())))
                .orElseThrow(() -> new IllegalStateException("Historical point not found"));

        BigDecimal historicalPrice = nz(nearest.buyPrice());
        if (historicalPrice.signum() <= 0) {
            throw new IllegalStateException("Historical price is invalid");
        }

        BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, symbol));
        if (currentPrice.signum() <= 0) {
            throw new IllegalStateException("Current price not found");
        }

        BigDecimal units = amountTry.divide(historicalPrice, 8, RoundingMode.HALF_UP);
        BigDecimal currentValue = units.multiply(currentPrice);
        BigDecimal pnl = currentValue.subtract(amountTry);

        BigDecimal pnlPct = amountTry.signum() == 0
                ? BigDecimal.ZERO
                : pnl.divide(amountTry, 6, RoundingMode.HALF_UP);

        String message = "%s tarihinde %s TRY %s yatırımı bugün %s TRY olurdu."
                .formatted(
                        buyDate,
                        amountTry.stripTrailingZeros().toPlainString(),
                        symbol,
                        currentValue.setScale(2, RoundingMode.HALF_UP).toPlainString()
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
                message
        );
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}