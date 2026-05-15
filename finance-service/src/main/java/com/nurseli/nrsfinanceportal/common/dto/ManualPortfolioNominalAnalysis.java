package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public record ManualPortfolioNominalAnalysis(
        BigDecimal buyCost,
        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal unrealizedProfit,
        BigDecimal unrealizedReturnPct,
        BigDecimal sellProceeds,
        BigDecimal realizedProfit,
        BigDecimal realizedReturnPct,
        BigDecimal holdValueToday,
        BigDecimal holdProfitToday,
        BigDecimal holdReturnPctToday,
        BigDecimal missedProfit,
        BigDecimal missedReturnPct,
        BigDecimal totalProfit,
        BigDecimal totalReturnPct
) {}
