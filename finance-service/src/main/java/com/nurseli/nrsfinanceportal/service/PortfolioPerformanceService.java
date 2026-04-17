package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.PerformanceItemDto;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioPerformanceDto;
import com.nurseli.nrsfinanceportal.common.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
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
                    : pnl.divide(cost, 6, RoundingMode.HALF_UP);

            items.add(new PerformanceItemDto(
                    p.getSource(),
                    p.getType(),
                    p.getSymbol(),
                    quantity,
                    avgBuy,
                    currentPrice,
                    cost,
                    currentValue,
                    pnl,
                    pnlPct
            ));

            totalCost = totalCost.add(cost);
            totalCurrentValue = totalCurrentValue.add(currentValue);
        }

        BigDecimal totalPnl = totalCurrentValue.subtract(totalCost);
        BigDecimal totalPnlPct = totalCost.signum() == 0
                ? BigDecimal.ZERO
                : totalPnl.divide(totalCost, 6, RoundingMode.HALF_UP);

        return new PortfolioPerformanceDto(
                totalCost,
                totalCurrentValue,
                totalPnl,
                totalPnlPct,
                items
        );
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}