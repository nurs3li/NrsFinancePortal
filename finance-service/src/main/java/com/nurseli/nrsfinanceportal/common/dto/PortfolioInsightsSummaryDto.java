package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public record PortfolioInsightsSummaryDto(
        BigDecimal openCurrentValue,
        BigDecimal closedRealizedValue,
        BigDecimal totalEvaluatedValue,
        BigDecimal totalInvestedAmount,
        BigDecimal nominalReturn,
        BigDecimal nominalReturnPct,
        BigDecimal inflationAdjustedCost,
        BigDecimal realReturn,
        BigDecimal realReturnPct,
        boolean realReturnAvailable,
        String realReturnUnavailableReason
) {}
