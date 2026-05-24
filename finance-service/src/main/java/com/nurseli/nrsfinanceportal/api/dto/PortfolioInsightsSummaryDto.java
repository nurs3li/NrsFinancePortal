package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;

/**
 * Portföy insight özet DTO'su; açık/kapalı pozisyon değerleri ve nominal/real return metriklerini taşır.
 */
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
