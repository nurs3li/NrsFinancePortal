package com.nurseli.whaleanalytics.domain;

import org.springframework.stereotype.Component;

@Component
public class RiskEvaluator {

    public RiskLevel evaluate(
            WhaleLevel whaleLevel,
            BehaviorClass behavior,
            PatternResult pattern
    ) {

        //  Manipulative pattern her zaman CRITICAL
        if (pattern.isManipulative()) {
            return RiskLevel.CRITICAL;
        }

        // Manipulative behavior da CRITICAL
        if (behavior == BehaviorClass.MANIPULATIVE) {
            return RiskLevel.CRITICAL;
        }

        //  L3 + Aggressive → HIGH
        if (whaleLevel == WhaleLevel.L3_MEGA_WHALE
                && behavior == BehaviorClass.AGGRESSIVE) {
            return RiskLevel.HIGH;
        }

        //  L2 + Strategic → MEDIUM
        if (whaleLevel == WhaleLevel.L2_WHALE
                && behavior == BehaviorClass.STRATEGIC) {
            return RiskLevel.MEDIUM;
        }

        //  L1 + Speculative → MEDIUM
        if (whaleLevel == WhaleLevel.L1_LARGE_TRADER
                && behavior == BehaviorClass.SPECULATIVE) {
            return RiskLevel.MEDIUM;
        }

        //  NONE + Conservative → LOW
        if (whaleLevel == WhaleLevel.NONE
                && behavior == BehaviorClass.CONSERVATIVE) {
            return RiskLevel.LOW;
        }

        //  Default fallback
        if (whaleLevel == WhaleLevel.L3_MEGA_WHALE) {
            return RiskLevel.HIGH;
        }

        if (whaleLevel == WhaleLevel.L2_WHALE) {
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }
}
