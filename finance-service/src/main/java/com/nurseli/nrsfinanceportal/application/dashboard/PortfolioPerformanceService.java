package com.nurseli.nrsfinanceportal.application.dashboard;

import com.nurseli.nrsfinanceportal.api.dto.PerformanceItemDto;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioPerformanceDto;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioSnapshotMetricsDto;
import com.nurseli.nrsfinanceportal.api.dto.UnifiedPortfolioItemView;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * finance-service portfolio performans servisi — birleşik portfolio için maliyet, değer ve K/Z metriklerini hesaplar.
 */
@RequiredArgsConstructor
@Service

public class PortfolioPerformanceService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UnifiedPortfolioService unifiedPortfolioService;
    private final MarketDataClient marketDataClient;

    @Transactional(readOnly = true)
    public PortfolioPerformanceDto myPerformance() {
        return computePerformance(unifiedPortfolioService.myUnifiedPortfolio());
    }

    /**
     * {@code performanceForUser} — Kullanıcının birleşik portfolio kalemleri için detaylı performans DTO'su üretir.
     */
    @Transactional(readOnly = true)
    public PortfolioPerformanceDto performanceForUser(User user) {
        return performanceForUser(user, marketDataClient.loadLatestPricing());
    }

    /**
     * {@code performanceForUser} — Önceden yüklenmiş fiyat snapshot'ı ile performans hesaplar (dashboard özeti için tek market-data çağrısı).
     */
    @Transactional(readOnly = true)
    public PortfolioPerformanceDto performanceForUser(User user, LatestPricingSnapshot pricing) {
        return computePerformance(unifiedPortfolioService.unifiedForUser(user), pricing);
    }

    /**
     * {@code computeSnapshotMetricsForUser} — Snapshot kaydı için toplam TRY değer, maliyet ve K/Z metriklerini hesaplar.
     */
    @Transactional(readOnly = true)
    public PortfolioSnapshotMetricsDto computeSnapshotMetricsForUser(User user) {
        List<UnifiedPortfolioItemView> portfolio = unifiedPortfolioService.unifiedForUser(user);
        LatestPricingSnapshot pricing = marketDataClient.loadLatestPricing();

        BigDecimal value = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

        for (UnifiedPortfolioItemView p : portfolio) {
            AssetType type = AssetType.valueOf(p.getType());
            BigDecimal quantity = nz(p.getQuantity());
            BigDecimal avgBuy = nz(p.getAvgBuyPrice());
            BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, p.getSymbol(), pricing));
            cost = cost.add(avgBuy.multiply(quantity));
            value = value.add(currentPrice.multiply(quantity));
        }

        return new PortfolioSnapshotMetricsDto(value, cost, value.subtract(cost));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private PortfolioPerformanceDto computePerformance(List<UnifiedPortfolioItemView> portfolio) {
        return computePerformance(portfolio, marketDataClient.loadLatestPricing());
    }

    private PortfolioPerformanceDto computePerformance(List<UnifiedPortfolioItemView> portfolio, LatestPricingSnapshot pricing) {
        List<PerformanceItemDto> items = new ArrayList<>();

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCurrentValue = BigDecimal.ZERO;

        for (UnifiedPortfolioItemView p : portfolio) {
            AssetType type = AssetType.valueOf(p.getType());
            BigDecimal quantity = nz(p.getQuantity());
            BigDecimal avgBuy = nz(p.getAvgBuyPrice());

            BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, p.getSymbol(), pricing));
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
}
