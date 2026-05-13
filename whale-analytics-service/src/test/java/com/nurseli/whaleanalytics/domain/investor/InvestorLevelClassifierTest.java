package com.nurseli.whaleanalytics.domain.investor;

import com.nurseli.whaleanalytics.config.InvestorBehaviorAnalysisProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvestorLevelClassifierTest {

    @Test
    void classifiesL3OnLargePortfolio() {
        InvestorBehaviorAnalysisProperties props = new InvestorBehaviorAnalysisProperties();
        InvestorLevelClassifier classifier = new InvestorLevelClassifier(props);
        PortfolioExposureSummary s = new PortfolioExposureSummary(
                new BigDecimal("6000000"),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1,
                1,
                0,
                "X",
                new BigDecimal("1000"),
                new BigDecimal("0.1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        PortfolioImpactBreakdown b = new PortfolioImpactBreakdown(10, 1, 1, 1, 1);
        assertThat(classifier.classify(s, b)).isEqualTo(InvestorLevel.L3_WHALE_PORTFOLIO);
    }

    @Test
    void normalWhenSmall() {
        InvestorBehaviorAnalysisProperties props = new InvestorBehaviorAnalysisProperties();
        InvestorLevelClassifier classifier = new InvestorLevelClassifier(props);
        PortfolioExposureSummary s = new PortfolioExposureSummary(
                new BigDecimal("10000"),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                2,
                2,
                0,
                "Y",
                new BigDecimal("5000"),
                new BigDecimal("0.5"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
        PortfolioImpactBreakdown b = new PortfolioImpactBreakdown(10, 1, 1, 1, 1);
        assertThat(classifier.classify(s, b)).isEqualTo(InvestorLevel.NORMAL);
    }
}
