package com.nurseli.nrsfinanceportal.dto.viop;

import java.math.BigDecimal;

public record ViopPositionSummaryDto(
        int openPositionCount,
        BigDecimal totalInitialMargin,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalRiskExposure,
        int longCount,
        int shortCount,
        int expiringSoonCount,
        BigDecimal netFinancialEffect,
        int incompleteDataCount
) {}
