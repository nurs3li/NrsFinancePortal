package com.nurseli.nrsfinanceportal.dto.bond;

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
        BigDecimal buyValue,
        BigDecimal currentValue,
        BigDecimal pnl,
        BigDecimal returnPct,
        BigDecimal annualCoupon,
        Integer daysToMaturity,
        String note
) {}
