package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.api.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.api.dto.PerformanceItemDto;
import com.nurseli.nrsfinanceportal.api.dto.PortfolioPerformanceDto;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.api.dto.bond.BondPositionSummaryDto;
import com.nurseli.nrsfinanceportal.api.dto.viop.ViopPositionSummaryDto;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import com.nurseli.nrsfinanceportal.application.bond.ManualBondPositionService;
import com.nurseli.nrsfinanceportal.application.viop.ManualViopPositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * finance-service dashboard özet servisi — spot portfolio, VIOP ve bond segmentlerini birleşik dashboard özetine dönüştürür.
 */
@RequiredArgsConstructor
@Service

public class DashboardSummaryService {

    private final PortfolioPerformanceService portfolioPerformanceService;
    private final ManualViopPositionService viopPositionService;
    private final ManualBondPositionService bondPositionService;
    private final UserRepository userRepository;

    /**
     * {@code getSummary} — Kullanıcının portfolio performansı, VIOP ve bond özetlerinden spot/futures segmentleri ve toplam portfolio değerini hesaplar.
     */
    public DashboardSummaryResponse getSummary(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return emptySummary();
        }

        PortfolioPerformanceDto performance = portfolioPerformanceService.performanceForUser(user);
        var portfolio = buildPortfolioSummary(performance);

        DashboardSummaryResponse.TradingSegmentSummary spot = new DashboardSummaryResponse.TradingSegmentSummary(
                nz(performance.getTotalCost()),
                nz(performance.getTotalPnl()),
                nz(performance.getTotalCurrentValue())
        );

        ViopPositionSummaryDto viop = viopPositionService.summaryForUser(user);
        BondPositionSummaryDto bond = bondPositionService.summaryForUser(user);

        BigDecimal futuresCost = nz(viop.totalInitialMargin()).add(nz(bond.totalNominalValue()));
        BigDecimal futuresPnl = nz(viop.totalUnrealizedPnl()).add(nz(bond.totalPnl()));
        BigDecimal viopNet = viop.netFinancialEffect() != null
                ? viop.netFinancialEffect()
                : nz(viop.totalInitialMargin()).add(nz(viop.totalUnrealizedPnl()));
        BigDecimal futuresValue = nz(bond.totalCurrentValue()).add(viopNet).setScale(6, RoundingMode.HALF_UP);

        DashboardSummaryResponse.TradingSegmentSummary futures = new DashboardSummaryResponse.TradingSegmentSummary(
                futuresCost.setScale(6, RoundingMode.HALF_UP),
                futuresPnl.setScale(6, RoundingMode.HALF_UP),
                futuresValue
        );

        BigDecimal totalPortfolio = spot.totalValueTry().add(futures.totalValueTry()).setScale(6, RoundingMode.HALF_UP);

        return new DashboardSummaryResponse(portfolio, totalPortfolio, spot, futures);
    }

    private static DashboardSummaryResponse emptySummary() {
        var zeroSegment = new DashboardSummaryResponse.TradingSegmentSummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        return new DashboardSummaryResponse(
                new DashboardSummaryResponse.PortfolioSummary(
                        BigDecimal.ZERO,
                        Map.of(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        List.of()
                ),
                BigDecimal.ZERO,
                zeroSegment,
                zeroSegment
        );
    }

    private static DashboardSummaryResponse.PortfolioSummary buildPortfolioSummary(PortfolioPerformanceDto perf) {
        Map<AssetType, BigDecimal> distribution = new EnumMap<>(AssetType.class);
        Map<AssetType, CategoryTotals> byType = new EnumMap<>(AssetType.class);

        for (PerformanceItemDto item : perf.getItems()) {
            AssetType t = AssetType.valueOf(item.getType());
            BigDecimal value = nz(item.getCurrentValue());
            BigDecimal cost = nz(item.getCost());
            distribution.merge(t, value, BigDecimal::add);
            byType.computeIfAbsent(t, k -> new CategoryTotals()).add(cost, value);
        }

        List<DashboardSummaryResponse.PortfolioCategoryBreakdown> categories = new ArrayList<>();
        for (Map.Entry<AssetType, CategoryTotals> e : byType.entrySet()) {
            CategoryTotals acc = e.getValue();
            BigDecimal pnl = acc.valueTry.subtract(acc.costTry);
            BigDecimal pnlPct = acc.costTry.signum() == 0
                    ? BigDecimal.ZERO
                    : pnl.divide(acc.costTry, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            categories.add(new DashboardSummaryResponse.PortfolioCategoryBreakdown(
                    e.getKey(),
                    acc.valueTry,
                    acc.costTry,
                    pnl,
                    pnlPct
            ));
        }
        categories.sort(Comparator.comparing(DashboardSummaryResponse.PortfolioCategoryBreakdown::valueTry).reversed());

        return new DashboardSummaryResponse.PortfolioSummary(
                nz(perf.getTotalCurrentValue()),
                distribution,
                nz(perf.getTotalCost()),
                nz(perf.getTotalPnl()),
                nz(perf.getTotalPnlPct()),
                List.copyOf(categories)
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static final class CategoryTotals {
        private BigDecimal costTry = BigDecimal.ZERO;
        private BigDecimal valueTry = BigDecimal.ZERO;

        void add(BigDecimal cost, BigDecimal value) {
            this.costTry = this.costTry.add(cost);
            this.valueTry = this.valueTry.add(value);
        }
    }
}
