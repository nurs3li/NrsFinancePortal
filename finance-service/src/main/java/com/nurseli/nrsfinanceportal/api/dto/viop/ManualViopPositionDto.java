package com.nurseli.nrsfinanceportal.api.dto.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;
import com.nurseli.nrsfinanceportal.domain.viop.ViopCloseReason;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manuel VIOP pozisyon detay DTO'su; giriş/kapanış, marjin, PnL ve risk maruziyeti metriklerini taşır.
 */
public record ManualViopPositionDto(
        Long id,
        String symbol,
        String displayName,
        ViopCategory viopCategory,
        String underlyingSymbol,
        ViopDirection direction,
        BigDecimal contractCount,
        BigDecimal entryPrice,
        LocalDate entryDate,
        BigDecimal currentPrice,
        BigDecimal contractMultiplier,
        BigDecimal initialMargin,
        /** Toplam başlangıç teminatı = tek sözleşme teminatı × kontrat adedi. */
        BigDecimal totalInitialMargin,
        LocalDate expiryDate,
        ViopPositionStatus status,
        BigDecimal closePrice,
        LocalDate closeDate,
        BigDecimal closeFee,
        ViopCloseReason closeReason,
        BigDecimal realizedPnl,
        BigDecimal realizedReturnPercent,
        /** Açık K/Z (TRY). */
        BigDecimal unrealizedPnl,
        /** Risk maruziyeti (TRY). */
        BigDecimal riskExposure,
        BigDecimal netFinancialEffect,
        Integer daysToExpiry,
        String note,
        String quoteCurrency,
        BigDecimal riskExposureNative,
        BigDecimal unrealizedPnlNative,
        BigDecimal leverage,
        BigDecimal marginRatio,
        BigDecimal pnlToMarginRatio,
        boolean missingFxRate
) {}
