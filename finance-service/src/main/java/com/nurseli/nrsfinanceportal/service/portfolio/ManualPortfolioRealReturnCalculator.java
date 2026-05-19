package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ManualPortfolioRealReturnCalculator {

    private static final int MONEY_SCALE = 8;
    private static final int PCT_SCALE = 6;

    private final MarketDataClient marketDataClient;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;

    public record PositionRealReturn(
            BigDecimal buyCost,
            BigDecimal evaluatedValue,
            BigDecimal inflationAdjustedCost,
            BigDecimal realReturn,
            BigDecimal realReturnPct,
            boolean included
    ) {}

    public record PortfolioRealReturnResult(
            BigDecimal openCurrentValue,
            BigDecimal closedRealizedValue,
            BigDecimal totalEvaluatedValue,
            BigDecimal totalInvestedAmount,
            BigDecimal nominalReturn,
            BigDecimal nominalReturnPct,
            BigDecimal inflationAdjustedCost,
            BigDecimal realReturn,
            BigDecimal realReturnPct,
            boolean realReturnAvailable,
            String realReturnUnavailableReason,
            List<PositionRealReturn> positions
    ) {}

    public PortfolioRealReturnResult compute(
            List<ManualPortfolioPosition> positions,
            CpiIndexLookup cpiLookup,
            LatestPricingSnapshot pricing) {

        if (positions == null || positions.isEmpty()) {
            return emptyResult();
        }

        Optional<BigDecimal> latestCpi = cpiLookup.latestIndex();
        boolean cpiOk = cpiLookup.isAvailable() && latestCpi.isPresent();

        BigDecimal openCurrentValue = BigDecimal.ZERO;
        BigDecimal closedRealizedValue = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal nominalReturnSum = BigDecimal.ZERO;
        BigDecimal inflationAdjustedTotal = BigDecimal.ZERO;
        BigDecimal realReturnTotal = BigDecimal.ZERO;
        boolean anyRealIncluded = false;
        List<PositionRealReturn> perPosition = new ArrayList<>();

        for (ManualPortfolioPosition p : positions) {
            BigDecimal priceTry = marketDataClient.getPriceTry(p.getType(), p.getSymbol(), pricing);
            ManualPortfolioNominalAnalysis nominal = nominalAnalysisCalculator.computeWithCurrentPrice(p, priceTry);
            if (nominal == null) {
                perPosition.add(new PositionRealReturn(BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, false));
                continue;
            }

            BigDecimal qty = nz(p.getQuantity());
            BigDecimal buyCost = nz(p.getBuyPrice()).multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            totalInvested = totalInvested.add(nz(nominal.buyCost()));

            BigDecimal evaluatedValue;
            BigDecimal nominalPnl;
            if (p.getStatus() == ManualPositionStatus.OPEN) {
                evaluatedValue = nominal.currentValue() != null
                        ? nominal.currentValue()
                        : BigDecimal.ZERO;
                openCurrentValue = openCurrentValue.add(evaluatedValue);
                nominalPnl = nominal.unrealizedProfit() != null ? nominal.unrealizedProfit() : BigDecimal.ZERO;
            } else {
                evaluatedValue = nz(p.getSellPrice()).multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                closedRealizedValue = closedRealizedValue.add(evaluatedValue);
                nominalPnl = nominal.realizedProfit() != null ? nominal.realizedProfit() : BigDecimal.ZERO;
            }
            nominalReturnSum = nominalReturnSum.add(nominalPnl);

            Optional<BigDecimal> buyCpi = cpiLookup.indexAtOrBefore(p.getBuyDate());
            Optional<BigDecimal> endCpi;
            if (p.getStatus() == ManualPositionStatus.OPEN) {
                endCpi = latestCpi;
            } else {
                LocalDate sellDate = p.getSellDate() != null ? p.getSellDate() : LocalDate.now();
                endCpi = cpiLookup.indexAtOrBefore(sellDate);
            }

            if (!cpiOk || buyCpi.isEmpty() || endCpi.isEmpty()) {
                perPosition.add(new PositionRealReturn(buyCost, evaluatedValue, null, null, null, false));
                continue;
            }

            BigDecimal cpiFactor = endCpi.get().divide(buyCpi.get(), MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal inflationAdjustedCost = buyCost.multiply(cpiFactor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal realReturn = evaluatedValue.subtract(inflationAdjustedCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal realReturnPct = pct(realReturn, inflationAdjustedCost);

            inflationAdjustedTotal = inflationAdjustedTotal.add(inflationAdjustedCost);
            realReturnTotal = realReturnTotal.add(realReturn);
            anyRealIncluded = true;
            perPosition.add(new PositionRealReturn(
                    buyCost, evaluatedValue, inflationAdjustedCost, realReturn, realReturnPct, true));
        }

        BigDecimal totalEvaluated = openCurrentValue.add(closedRealizedValue);
        BigDecimal nominalReturnPct = pct(nominalReturnSum, totalInvested);

        if (!anyRealIncluded) {
            String reason = !cpiLookup.isAvailable()
                    ? "TÜFE endeks geçmişi market-data üzerinden alınamadı."
                    : "Bazı pozisyonlar için alım/satış dönemine ait TÜFE endeksi çözümlenemedi.";
            return new PortfolioRealReturnResult(
                    openCurrentValue,
                    closedRealizedValue,
                    totalEvaluated,
                    totalInvested,
                    nominalReturnSum,
                    nominalReturnPct,
                    null,
                    null,
                    null,
                    false,
                    reason,
                    perPosition
            );
        }

        BigDecimal portfolioRealReturnPct = pct(realReturnTotal, inflationAdjustedTotal);
        return new PortfolioRealReturnResult(
                openCurrentValue,
                closedRealizedValue,
                totalEvaluated,
                totalInvested,
                nominalReturnSum,
                nominalReturnPct,
                inflationAdjustedTotal,
                realReturnTotal,
                portfolioRealReturnPct,
                true,
                null,
                perPosition
        );
    }

    private static PortfolioRealReturnResult emptyResult() {
        return new PortfolioRealReturnResult(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                false,
                "Manuel portföy kaydı bulunmuyor.",
                List.of()
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal pct(BigDecimal profit, BigDecimal base) {
        if (profit == null || base == null || base.signum() <= 0) {
            return null;
        }
        return profit.divide(base, PCT_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }
}
