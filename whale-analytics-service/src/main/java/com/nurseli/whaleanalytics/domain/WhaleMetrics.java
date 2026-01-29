package com.nurseli.whaleanalytics.domain;

import java.math.BigDecimal;

public record WhaleMetrics(
        BigDecimal dailyVolume,
        int hourlyTransactionCount,
        BigDecimal maxSingleTransaction
) {
}
