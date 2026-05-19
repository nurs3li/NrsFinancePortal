package com.nurseli.marketdata.api.dto.eurobond;

import java.math.BigDecimal;

public record EurobondShareMetricsDto(
        BigDecimal usdSharePct,
        BigDecimal eurSharePct,
        BigDecimal jpySharePct,
        BigDecimal remainingLongSharePct,
        BigDecimal remainingShortSharePct) {

    public static EurobondShareMetricsDto empty() {
        return new EurobondShareMetricsDto(null, null, null, null, null);
    }
}
