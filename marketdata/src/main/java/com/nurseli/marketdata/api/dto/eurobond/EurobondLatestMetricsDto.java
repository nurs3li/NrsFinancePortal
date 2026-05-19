package com.nurseli.marketdata.api.dto.eurobond;

import java.math.BigDecimal;

public record EurobondLatestMetricsDto(
        BigDecimal marketValue,
        BigDecimal bookValue,
        BigDecimal totalDistribution,
        BigDecimal remainingShort,
        BigDecimal remainingLong,
        BigDecimal originalShort,
        BigDecimal originalLong,
        BigDecimal usdIssues,
        BigDecimal eurIssues,
        BigDecimal jpyIssues) {

    public static EurobondLatestMetricsDto empty() {
        return new EurobondLatestMetricsDto(null, null, null, null, null, null, null, null, null, null);
    }
}
