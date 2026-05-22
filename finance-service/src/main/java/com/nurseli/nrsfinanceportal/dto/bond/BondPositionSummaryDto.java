package com.nurseli.nrsfinanceportal.dto.bond;

import java.math.BigDecimal;
import java.util.Map;

public record BondPositionSummaryDto(
        int openPositionCount,
        BigDecimal totalNominalValue,
        BigDecimal totalCurrentValue,
        BigDecimal totalPnl,
        BigDecimal totalPricePnl,
        BigDecimal totalCollectedCoupon,
        BigDecimal averageReturnPct,
        BigDecimal annualCouponEstimate,
        int expiringSoonCount,
        Map<String, BigDecimal> currencyBreakdown,
        int incompleteDataCount
) {}
