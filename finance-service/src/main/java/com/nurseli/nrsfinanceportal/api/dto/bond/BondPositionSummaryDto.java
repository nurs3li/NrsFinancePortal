package com.nurseli.nrsfinanceportal.api.dto.bond;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Bond pozisyon özet DTO'su; açık pozisyon sayısı, nominal/değer, PnL ve vade yaklaşan sayısını taşır.
 */
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
