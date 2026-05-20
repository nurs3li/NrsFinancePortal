package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public record ViopBondCombinedSummaryDto(
        BigDecimal totalFinancialEffect,
        BigDecimal totalRiskExposure,
        int totalExpiringSoon,
        Integer activeAlertCount,
        BigDecimal classicPortfolioValue,
        BigDecimal bondCurrentValue,
        BigDecimal viopNetEffect,
        BigDecimal viopTotalInitialMargin,
        BigDecimal viopTotalUnrealizedPnl
) {}
