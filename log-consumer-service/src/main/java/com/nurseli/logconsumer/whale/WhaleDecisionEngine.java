package com.nurseli.logconsumer.whale;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class WhaleDecisionEngine {

    // Eşikler (ileride config yapılabilir)
    private static final BigDecimal L1_SINGLE_TX = new BigDecimal("250000");
    private static final BigDecimal L2_DAILY_VOL = new BigDecimal("1000000");
    private static final BigDecimal L3_DAILY_VOL = new BigDecimal("5000000");
    private static final BigDecimal L3_SINGLE_TX = new BigDecimal("1000000");

    public WhaleLevel evaluate(WhaleMetrics m) {

        // L3 – Market Mover
        if (m.dailyVolume().compareTo(L3_DAILY_VOL) >= 0
                && m.hourlyTransactionCount() >= 10) {
            return WhaleLevel.L3_MARKET_MOVER;
        }
        if (m.maxSingleTransaction().compareTo(L3_SINGLE_TX) >= 0) {
            return WhaleLevel.L3_MARKET_MOVER;
        }

        // L2 – Whale
        if (m.dailyVolume().compareTo(L2_DAILY_VOL) >= 0
                && m.hourlyTransactionCount() >= 5) {
            return WhaleLevel.L2_WHALE;
        }

        // L1 – Large Trader
        if (m.maxSingleTransaction().compareTo(L1_SINGLE_TX) >= 0
                || m.hourlyTransactionCount() >= 10) {
            return WhaleLevel.L1_LARGE_TRADER;
        }

        return WhaleLevel.NONE;
    }
}
