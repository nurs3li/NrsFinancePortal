package com.nurseli.nrsfinanceportal.dto.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;
import com.nurseli.nrsfinanceportal.domain.viop.ViopCloseReason;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        LocalDate expiryDate,
        ViopPositionStatus status,
        BigDecimal closePrice,
        LocalDate closeDate,
        BigDecimal closeFee,
        ViopCloseReason closeReason,
        BigDecimal realizedPnl,
        BigDecimal realizedReturnPercent,
        BigDecimal unrealizedPnl,
        BigDecimal riskExposure,
        BigDecimal netFinancialEffect,
        Integer daysToExpiry,
        String note
) {}
