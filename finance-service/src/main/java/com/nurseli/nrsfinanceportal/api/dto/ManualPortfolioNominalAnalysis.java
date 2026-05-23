package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;

/**
 * Manuel portföy nominal analiz DTO'su; maliyet, gerçekleşen/gerçekleşmemiş kâr ve tutma senaryosu metriklerini taşır.
 */
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
