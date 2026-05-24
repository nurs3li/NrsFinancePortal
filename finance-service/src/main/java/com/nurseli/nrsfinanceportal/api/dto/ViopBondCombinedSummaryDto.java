package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;

/**
 * VIOP ve bond birleşik özet DTO'su; klasik portföy, tahvil ve VIOP finansal etki metriklerini taşır.
 */
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
