package com.nurseli.whaleanalytics.domain.investor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioImpactScoreCalculatorTest {

    @Test
    void smallPortfolioLowScore() {
        PortfolioExposureSummary s = new PortfolioExposureSummary(
                new BigDecimal("10000"),
                new BigDecimal("9000"),
                new BigDecimal("1000"),
                new BigDecimal("800"),
                2,
                2,
                0,
                "AAA",
                new BigDecimal("6000"),
                new BigDecimal("0.60"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        PortfolioImpactBreakdown b = PortfolioImpactScoreCalculator.compute(s);
        assertThat(b.portfolioImpactScore()).isLessThanOrEqualTo(100);
        assertThat(b.portfolioImpactScore()).isPositive();
    }

    @Test
    void negativeRealProfitLowersRealComponent() {
        PortfolioExposureSummary s = new PortfolioExposureSummary(
                new BigDecimal("500000"),
                new BigDecimal("400000"),
                new BigDecimal("100000"),
                new BigDecimal("-20000"),
                3,
                3,
                0,
                "X",
                new BigDecimal("300000"),
                new BigDecimal("0.60"),
                new BigDecimal("0.5"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        PortfolioImpactBreakdown b = PortfolioImpactScoreCalculator.compute(s);
        assertThat(b.realProfitScore()).isLessThanOrEqualTo(5);
    }
}
