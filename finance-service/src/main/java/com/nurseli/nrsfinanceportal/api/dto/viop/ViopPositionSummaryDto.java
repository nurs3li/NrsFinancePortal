package com.nurseli.nrsfinanceportal.api.dto.viop;

import java.math.BigDecimal;

/**
 * VIOP pozisyon özet DTO'su; açık pozisyon sayısı, marjin, PnL, kaldıraç ve risk metriklerini taşır.
 */
public record ViopPositionSummaryDto(
        int openPositionCount,
        BigDecimal totalInitialMargin,
        BigDecimal totalUnrealizedPnl,
        /** Toplam risk maruziyeti (TRY karşılığı). */
        BigDecimal totalRiskExposure,
        int longCount,
        int shortCount,
        int expiringSoonCount,
        BigDecimal netFinancialEffect,
        int incompleteDataCount,
        /** totalRiskExposure / totalInitialMargin */
        BigDecimal portfolioLeverage,
        /** totalInitialMargin / totalRiskExposure (oran, UI %100 ile çarpar) */
        BigDecimal marginRatio,
        /** totalUnrealizedPnl / totalInitialMargin */
        BigDecimal pnlToMarginRatio,
        boolean hasMissingFxRate
) {}
