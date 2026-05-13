package com.nurseli.whaleanalytics.domain.investor;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PortfolioImpactScoreCalculator {

    private PortfolioImpactScoreCalculator() {
    }

    public static PortfolioImpactBreakdown compute(PortfolioExposureSummary summary) {
        BigDecimal totalVal = nz(summary.totalPortfolioValueTry());
        BigDecimal largestRatio = summary.largestPositionRatio() != null
                ? summary.largestPositionRatio()
                : BigDecimal.ZERO;
        BigDecimal largestVal = nz(summary.largestPositionValueTry());

        int sizeScore = portfolioSizeScore(totalVal);
        int concentrationScore = concentrationRatioScore(largestRatio);
        int largePositionScore = largePositionValueScore(largestVal);
        int profitPart = nominalProfitComponent(summary.totalNominalProfitTry());
        int realProfitPart = realProfitComponent(summary.totalNominalProfitTry(), summary.totalRealProfitTry());
        int riskPart = riskScore(summary.cryptoExposureRatio(), largestRatio);
        int diversificationScore = diversificationScore(summary.positionCount(), largestRatio);

        int total = Math.min(
                100,
                sizeScore + concentrationScore + largePositionScore + profitPart + realProfitPart + riskPart + diversificationScore
        );

        int assetConcentration = Math.min(100, concentrationScore + largePositionScore + diversificationScore);
        return new PortfolioImpactBreakdown(total, assetConcentration, profitPart, realProfitPart, riskPart);
    }

    private static int portfolioSizeScore(BigDecimal totalTry) {
        BigDecimal v = nz(totalTry);
        if (v.compareTo(new BigDecimal("50000")) < 0) {
            return linear(v, BigDecimal.ZERO, new BigDecimal("50000"), 0, 5);
        }
        if (v.compareTo(new BigDecimal("250000")) < 0) {
            return linear(v, new BigDecimal("50000"), new BigDecimal("250000"), 5, 12);
        }
        if (v.compareTo(new BigDecimal("1000000")) < 0) {
            return linear(v, new BigDecimal("250000"), new BigDecimal("1000000"), 12, 22);
        }
        return linear(v, new BigDecimal("1000000"), new BigDecimal("10000000"), 22, 30);
    }

    private static int concentrationRatioScore(BigDecimal ratio) {
        BigDecimal r = ratio == null ? BigDecimal.ZERO : ratio.max(BigDecimal.ZERO);
        if (r.compareTo(new BigDecimal("0.20")) < 0) {
            return linear(r, BigDecimal.ZERO, new BigDecimal("0.20"), 0, 5);
        }
        if (r.compareTo(new BigDecimal("0.40")) < 0) {
            return linear(r, new BigDecimal("0.20"), new BigDecimal("0.40"), 5, 10);
        }
        if (r.compareTo(new BigDecimal("0.60")) < 0) {
            return linear(r, new BigDecimal("0.40"), new BigDecimal("0.60"), 10, 16);
        }
        return linear(r, new BigDecimal("0.60"), BigDecimal.ONE, 16, 20);
    }

    private static int largePositionValueScore(BigDecimal largestVal) {
        BigDecimal v = nz(largestVal);
        if (v.compareTo(new BigDecimal("50000")) < 0) {
            return linear(v, BigDecimal.ZERO, new BigDecimal("50000"), 0, 4);
        }
        if (v.compareTo(new BigDecimal("250000")) < 0) {
            return linear(v, new BigDecimal("50000"), new BigDecimal("250000"), 4, 9);
        }
        if (v.compareTo(new BigDecimal("1000000")) < 0) {
            return linear(v, new BigDecimal("250000"), new BigDecimal("1000000"), 9, 13);
        }
        return linear(v, new BigDecimal("1000000"), new BigDecimal("5000000"), 13, 15);
    }

    private static int nominalProfitComponent(BigDecimal nominal) {
        if (nominal == null || nominal.signum() <= 0) {
            return nominal != null && nominal.signum() < 0 ? 0 : 2;
        }
        if (nominal.compareTo(new BigDecimal("50000")) < 0) {
            return linear(nominal, BigDecimal.ZERO, new BigDecimal("50000"), 3, 6);
        }
        if (nominal.compareTo(new BigDecimal("250000")) < 0) {
            return linear(nominal, new BigDecimal("50000"), new BigDecimal("250000"), 6, 8);
        }
        return linear(nominal, new BigDecimal("250000"), new BigDecimal("5000000"), 8, 10);
    }

    private static int realProfitComponent(BigDecimal nominal, BigDecimal real) {
        BigDecimal n = nominal == null ? BigDecimal.ZERO : nominal;
        BigDecimal r = real == null ? BigDecimal.ZERO : real;
        if (n.signum() > 0 && r.signum() < 0) {
            return 1;
        }
        if (r.signum() <= 0) {
            return r.signum() < 0 ? 0 : 2;
        }
        if (r.compareTo(new BigDecimal("25000")) < 0) {
            return linear(r, BigDecimal.ZERO, new BigDecimal("25000"), 3, 6);
        }
        return linear(r, new BigDecimal("25000"), new BigDecimal("2000000"), 6, 10);
    }

    private static int riskScore(BigDecimal cryptoRatio, BigDecimal concentration) {
        BigDecimal c = cryptoRatio == null ? BigDecimal.ZERO : cryptoRatio.max(BigDecimal.ZERO);
        BigDecimal conc = concentration == null ? BigDecimal.ZERO : concentration;
        int cryptoPart = linear(c, BigDecimal.ZERO, new BigDecimal("0.80"), 0, 6);
        int concPart = linear(conc, new BigDecimal("0.50"), BigDecimal.ONE, 0, 4);
        return Math.min(10, cryptoPart + concPart);
    }

    private static int diversificationScore(int positions, BigDecimal concentration) {
        if (positions <= 1 && concentration != null && concentration.compareTo(new BigDecimal("0.45")) > 0) {
            return 5;
        }
        if (positions <= 3 && concentration != null && concentration.compareTo(new BigDecimal("0.55")) > 0) {
            return 4;
        }
        return 1;
    }

    private static int linear(BigDecimal x, BigDecimal min, BigDecimal max, int scoreMin, int scoreMax) {
        if (max.compareTo(min) <= 0) {
            return scoreMax;
        }
        if (x.compareTo(min) <= 0) {
            return scoreMin;
        }
        if (x.compareTo(max) >= 0) {
            return scoreMax;
        }
        BigDecimal t = x.subtract(min).divide(max.subtract(min), 6, RoundingMode.HALF_UP);
        int span = scoreMax - scoreMin;
        return scoreMin + t.multiply(BigDecimal.valueOf(span)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
