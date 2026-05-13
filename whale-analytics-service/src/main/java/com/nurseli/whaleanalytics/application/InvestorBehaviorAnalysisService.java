package com.nurseli.whaleanalytics.application;

import com.nurseli.whaleanalytics.config.InvestorBehaviorAnalysisProperties;
import com.nurseli.whaleanalytics.domain.investor.InvestorBehaviorExplanationBuilder;
import com.nurseli.whaleanalytics.domain.investor.InvestorLevel;
import com.nurseli.whaleanalytics.domain.investor.InvestorLevelClassifier;
import com.nurseli.whaleanalytics.domain.investor.PortfolioExposureAggregator;
import com.nurseli.whaleanalytics.domain.investor.PortfolioExposureSummary;
import com.nurseli.whaleanalytics.domain.investor.PortfolioImpactBreakdown;
import com.nurseli.whaleanalytics.domain.investor.PortfolioImpactScoreCalculator;
import com.nurseli.whaleanalytics.event.investment.InvestorBehaviorUpdatedEvent;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisInvestorPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvestorBehaviorAnalysisService {

    private final RedisInvestorPositionRepository positionRepository;
    private final InvestorLevelClassifier levelClassifier;
    private final InvestorBehaviorAnalysisProperties analysisProperties;

    public InvestorBehaviorUpdatedEvent analyze(long userId) {
        var snapshots = positionRepository.findAllForUser(userId);
        PortfolioExposureSummary summary = PortfolioExposureAggregator.aggregate(snapshots);
        PortfolioImpactBreakdown breakdown = PortfolioImpactScoreCalculator.compute(summary);
        InvestorLevel level = levelClassifier.classify(summary, breakdown);
        List<String> explanations = InvestorBehaviorExplanationBuilder.build(
                summary, breakdown, level, analysisProperties);

        return new InvestorBehaviorUpdatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                userId,
                level,
                breakdown.portfolioImpactScore(),
                nz(summary.totalPortfolioValueTry()),
                nz(summary.totalInvestedAmountTry()),
                nz(summary.totalNominalProfitTry()),
                nz(summary.totalRealProfitTry()),
                summary.largestPositionSymbol(),
                nz(summary.largestPositionValueTry()),
                summary.largestPositionRatio() != null ? summary.largestPositionRatio() : BigDecimal.ZERO,
                breakdown.assetConcentrationScore(),
                breakdown.profitScore(),
                breakdown.realProfitScore(),
                breakdown.riskExposureScore(),
                summary.positionCount(),
                summary.openPositionCount(),
                summary.closedPositionCount(),
                summary.cryptoExposureRatio(),
                summary.equityExposureRatio(),
                summary.fxExposureRatio(),
                summary.fundExposureRatio(),
                summary.metalExposureRatio(),
                explanations
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
