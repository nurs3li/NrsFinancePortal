package com.nurseli.logconsumer.whale;

import java.math.BigDecimal;

public record WhaleMetrics(
        int hourlyTransactionCount,
        BigDecimal dailyVolume,
        BigDecimal maxSingleTransaction
) {
}
