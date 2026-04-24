package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.PerformanceItemDto;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioPerformanceDto;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioSnapshotMetricsDto;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortfolioPerformanceService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UnifiedPortfolioService unifiedPortfolioService;
    private final MarketDataClient marketDataClient;

    @Transactional(readOnly = true)
    public PortfolioPerformanceDto myPerformance() {
        List<UnifiedPortfolioItemView> portfolio = unifiedPortfolioService.myUnifiedPortfolio();
        List<PerformanceItemDto> items = new ArrayList<>();

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;

        for (UnifiedPortfolioItemView p : portfolio) {
            AssetType type = AssetType.valueOf(p.getType());
            BigDecimal quantity = nz(p.getQuantity());
            BigDecimal avgBuy = nz(p.getAvgBuyPrice());

            BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, p.getSymbol()));
            BigDecimal cost = avgBuy.multiply(quantity);
            BigDecimal currentValue = currentPrice.multiply(quantity);
            BigDecimal pnl = currentValue.subtract(cost);

            BigDecimal pnlPct = cost.signum() == 0
                    ? BigDecimal.ZERO
                    : pnl.divide(cost, 6, RoundingMode.HALF_UP).multiply(HUNDRED);

            items.add(new PerformanceItemDto(
                    p.getSource(),
                    p.getType(),
                    p.getSymbol(),
                    quantity,
                    avgBuy,
                    currentPrice,
                    "TRY",
                    cost,
                    currentValue,
                    pnl,
                    pnlPct,
                    p.getManualPositionId()
            ));

            totalCost = totalCost.add(cost);
            totalCurrentValue = totalCurrentValue.add(currentValue);
        }

        BigDecimal totalPnl = totalCurrentValue.subtract(totalCost);
        BigDecimal totalPnlPct = totalCost.signum() == 0
                ? BigDecimal.ZERO
                : totalPnl.divide(totalCost, 6, RoundingMode.HALF_UP).multiply(HUNDRED);

        return new PortfolioPerformanceDto(
                totalCost,
                totalCurrentValue,
                totalPnl,
                totalPnlPct,
                items
        );
    }

    /**
     * Snapshot / grafik için: trade ve manuel kovalarına ayrılmış değer-maliyet-kar.
     */
    @Transactional(readOnly = true)
    public PortfolioSnapshotMetricsDto computeSnapshotMetricsForUser(User user) {
        List<UnifiedPortfolioItemView> portfolio = unifiedPortfolioService.unifiedForUser(user);

        BigDecimal tradeValue = BigDecimal.ZERO;
        BigDecimal tradeCost = BigDecimal.ZERO;
        BigDecimal manualValue = BigDecimal.ZERO;
        BigDecimal manualCost = BigDecimal.ZERO;

        for (UnifiedPortfolioItemView p : portfolio) {
            AssetType type = AssetType.valueOf(p.getType());
            BigDecimal quantity = nz(p.getQuantity());
            BigDecimal avgBuy = nz(p.getAvgBuyPrice());
            BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, p.getSymbol()));
            BigDecimal cost = avgBuy.multiply(quantity);
            BigDecimal currentValue = currentPrice.multiply(quantity);

            if ("MANUAL".equalsIgnoreCase(p.getSource())) {
                manualValue = manualValue.add(currentValue);
                manualCost = manualCost.add(cost);
            } else {
                tradeValue = tradeValue.add(currentValue);
                tradeCost = tradeCost.add(cost);
            }
        }

        BigDecimal combinedValue = tradeValue.add(manualValue);
        BigDecimal combinedCost = tradeCost.add(manualCost);
        BigDecimal tradePnl = tradeValue.subtract(tradeCost);
        BigDecimal manualPnl = manualValue.subtract(manualCost);
        BigDecimal combinedPnl = combinedValue.subtract(combinedCost);

        return new PortfolioSnapshotMetricsDto(
                combinedValue,
                combinedCost,
                combinedPnl,
                tradeValue,
                tradeCost,
                tradePnl,
                manualValue,
                manualCost,
                manualPnl
        );
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}