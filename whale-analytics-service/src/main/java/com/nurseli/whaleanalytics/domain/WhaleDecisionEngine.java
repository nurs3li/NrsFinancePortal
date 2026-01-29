package com.nurseli.whaleanalytics.domain;

import com.nurseli.whaleanalytics.config.WhaleThresholdProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhaleDecisionEngine {

    private final WhaleThresholdProperties thresholds;

    public WhaleLevel evaluate(WhaleMetrics metrics) {

        // 🔥 L3 – MEGA WHALE (Market Mover)
        if (isL3(metrics)) {
            return WhaleLevel.L3_MEGA_WHALE;
        }

        // 🐋 L2 – WHALE
        if (isL2(metrics)) {
            return WhaleLevel.L2_WHALE;
        }

        // 💰 L1 – LARGE TRADER
        if (isL1(metrics)) {
            return WhaleLevel.L1_LARGE_TRADER;
        }

        return WhaleLevel.NONE;
    }

    /* ===================== PRIVATE RULES ===================== */

    private boolean isL3(WhaleMetrics m) {
        return
                (m.dailyVolume().compareTo(thresholds.getL3().getDailyVolume()) >= 0
                        && m.hourlyTransactionCount() >= thresholds.getL3().getHourlyCount())
                        ||
                        m.maxSingleTransaction().compareTo(thresholds.getL3().getSingleTx()) >= 0;
    }

    private boolean isL2(WhaleMetrics m) {
        return
                m.dailyVolume().compareTo(thresholds.getL2().getDailyVolume()) >= 0
                        && m.hourlyTransactionCount() >= thresholds.getL2().getHourlyCount();
    }

    private boolean isL1(WhaleMetrics m) {
        return
                m.maxSingleTransaction().compareTo(thresholds.getL1().getSingleTx()) >= 0
                        || m.hourlyTransactionCount() >= thresholds.getL1().getHourlyCount();
    }
}
