package com.nurseli.whaleanalytics.domain.investor;

import com.nurseli.whaleanalytics.config.InvestorBehaviorAnalysisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class InvestorLevelClassifier {

    private final InvestorBehaviorAnalysisProperties properties;

    public InvestorLevel classify(PortfolioExposureSummary summary, PortfolioImpactBreakdown breakdown) {
        var t = properties.getThresholds();
        BigDecimal totalVal = nz(summary.totalPortfolioValueTry());
        BigDecimal largestVal = nz(summary.largestPositionValueTry());
        BigDecimal ratio = summary.largestPositionRatio() != null ? summary.largestPositionRatio() : BigDecimal.ZERO;
        int score = breakdown.portfolioImpactScore();

        boolean l3 = totalVal.compareTo(t.getL3PortfolioTry()) >= 0
                || largestVal.compareTo(t.getL3LargestPositionTry()) >= 0
                || score >= t.getL3Score();

        boolean l2 = (totalVal.compareTo(t.getL2PortfolioTry()) >= 0
                && ratio.compareTo(t.getL2ConcentrationRatio()) >= 0)
                || score >= t.getL2Score();

        boolean l1 = totalVal.compareTo(t.getL1PortfolioTry()) >= 0
                || largestVal.compareTo(t.getL1LargestPositionTry()) >= 0
                || score >= t.getL1Score();

        if (l3) {
            return InvestorLevel.L3_WHALE_PORTFOLIO;
        }
        if (l2) {
            return InvestorLevel.L2_HIGH_IMPACT_INVESTOR;
        }
        if (l1) {
            return InvestorLevel.L1_LARGE_INVESTOR;
        }
        return InvestorLevel.NORMAL;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
