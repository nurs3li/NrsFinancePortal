package com.nurseli.nrsfinanceportal.dto.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondCloseType;
import com.nurseli.nrsfinanceportal.domain.bond.BondPositionStatus;
import com.nurseli.nrsfinanceportal.domain.bond.BondType;
import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ManualBondPositionDto(
        Long id,
        String symbol,
        String displayName,
        BondType bondType,
        String currency,
        BigDecimal nominalValue,
        BigDecimal buyPrice,
        LocalDate buyDate,
        BigDecimal currentPrice,
        LocalDate maturityDate,
        BigDecimal couponRate,
        CouponFrequency couponFrequency,
        BondPositionStatus status,
        BigDecimal sellPrice,
        LocalDate sellDate,
        BondCloseType closeType,
        BigDecimal closeFee,
        BigDecimal collectedCouponAmount,
        BigDecimal realizedPnl,
        BigDecimal realizedReturnPercent,
        BigDecimal buyValue,
        BigDecimal currentValue,
        BigDecimal pnl,
        BigDecimal pricePnl,
        BigDecimal returnPct,
        BigDecimal annualCoupon,
        BigDecimal periodicCoupon,
        Integer completedCouponPeriods,
        BigDecimal collectedCoupon,
        BigDecimal estimatedAccruedCoupon,
        BigDecimal totalReturn,
        BigDecimal totalReturnPercent,
        Integer daysToMaturity,
        String note
) {}
