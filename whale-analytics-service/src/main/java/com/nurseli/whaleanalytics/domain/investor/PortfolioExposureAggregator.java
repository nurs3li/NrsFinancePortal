package com.nurseli.whaleanalytics.domain.investor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class PortfolioExposureAggregator {

    private PortfolioExposureAggregator() {
    }

    public static PortfolioExposureSummary aggregate(Collection<InvestorPositionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return PortfolioExposureSummary.empty();
        }

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalNominal = BigDecimal.ZERO;
        BigDecimal totalReal = BigDecimal.ZERO;
        int open = 0;
        int closed = 0;

        Map<String, BigDecimal> byType = new HashMap<>();

        String largestSym = null;
        BigDecimal largestVal = BigDecimal.ZERO;

        for (InvestorPositionSnapshot s : snapshots) {
            BigDecimal invested = nz(s.investedAmountTry());
            BigDecimal contrib = contributionValue(s);
            totalInvested = totalInvested.add(invested);
            totalValue = totalValue.add(contrib);

            BigDecimal nom = s.nominalProfitTry();
            if (nom != null) {
                totalNominal = totalNominal.add(nom);
            }
            BigDecimal re = s.realProfitTry();
            if (re != null) {
                totalReal = totalReal.add(re);
            }

            if (isClosed(s.status())) {
                closed++;
            } else {
                open++;
            }

            String type = s.assetType() != null ? s.assetType().toUpperCase() : "OTHER";
            byType.merge(type, contrib, BigDecimal::add);

            if (largestSym == null || contrib.compareTo(largestVal) > 0) {
                largestVal = contrib;
                largestSym = s.symbol();
            }
        }

        int count = snapshots.size();
        BigDecimal largestRatio = totalValue.signum() == 0
                ? BigDecimal.ZERO
                : largestVal.divide(totalValue, 6, RoundingMode.HALF_UP);

        BigDecimal crypto = ratio(byType.get("CRYPTO"), totalValue);
        BigDecimal equity = ratio(byType.get("STOCK"), totalValue);
        BigDecimal fx = ratio(byType.get("FX"), totalValue);
        BigDecimal fund = ratio(byType.get("FUND"), totalValue);
        BigDecimal metal = ratio(byType.get("METAL"), totalValue);

        return new PortfolioExposureSummary(
                scaleMoney(totalValue),
                scaleMoney(totalInvested),
                scaleMoney(totalNominal),
                scaleMoney(totalReal),
                count,
                open,
                closed,
                largestSym,
                scaleMoney(largestVal),
                largestRatio,
                crypto,
                equity,
                fx,
                fund,
                metal
        );
    }

    private static BigDecimal contributionValue(InvestorPositionSnapshot s) {
        if (isClosed(s.status())) {
            return nz(s.closedValueTry(), s.investedAmountTry());
        }
        return nz(s.currentValueTry(), s.investedAmountTry());
    }

    private static boolean isClosed(String status) {
        return status != null && status.equalsIgnoreCase("CLOSED");
    }

    private static BigDecimal nz(BigDecimal primary, BigDecimal fallback) {
        if (primary != null && primary.signum() != 0) {
            return primary;
        }
        return fallback != null ? fallback : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal ratio(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.divide(total, 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleMoney(BigDecimal v) {
        return v.setScale(8, RoundingMode.HALF_UP);
    }
}
