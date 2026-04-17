package com.nurseli.whaleanalytics.application;

import com.nurseli.whaleanalytics.domain.*;
import com.nurseli.whaleanalytics.infrastructure.redis.RedisWhaleReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhaleAnalysisService {

    private final RedisWhaleReadRepository readRepository;
    private final WhaleDecisionEngine decisionEngine;
    private final WhaleImpactScoreCalculator impactScoreCalculator;

    private final TrendAnalyzer trendAnalyzer;
    private final PatternDetector patternDetector;
    private final BehaviorClassifier behaviorClassifier;
    private final RiskEvaluator riskEvaluator;
    private final ImpactScoreNormalizer impactScoreNormalizer;

    public WhaleDecisionResult analyze(String userId) {

        WhaleMetrics metrics = readRepository.getMetrics(userId);

        Duration lookback = Duration.ofHours(24);
        List<TransactionSnapshot> history =
                readRepository.getRecentTransactions(userId, lookback);

        TrendResult trend = trendAnalyzer.analyze(history);
        PatternResult pattern = patternDetector.detect(history);

        WhaleLevel level = decisionEngine.evaluate(metrics);

        BehaviorClass behavior = behaviorClassifier.classify(metrics, trend, pattern);
        RiskLevel risk = riskEvaluator.evaluate(level, behavior, pattern);

        int rawImpact = impactScoreCalculator.calculate(metrics, level);
        int impactScore = impactScoreNormalizer.normalize(
                metrics, level, trend, pattern, behavior, risk, rawImpact
        );

        Instant now = Instant.now();

        log.warn("""
            WHALE FULL ANALYSIS
            userId      : {}
            level       : {}
            trend       : {}
            velocity    : {}
            volatility  : {}
            pattern     : {}
            behavior    : {}
            risk        : {}
            impactScore : {}
            """,
                userId,
                level,
                trend.direction(),
                trend.velocity(),
                trend.volatility(),
                pattern.dominantPattern(),
                behavior,
                risk,
                impactScore
        );

        return new WhaleDecisionResult(
                userId,
                level,
                metrics,
                trend,
                pattern,
                behavior,
                risk,
                impactScore,
                now
        );
    }

}