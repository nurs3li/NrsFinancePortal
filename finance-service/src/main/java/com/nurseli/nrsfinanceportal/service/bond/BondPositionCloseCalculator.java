package com.nurseli.nrsfinanceportal.service.bond;

import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BondPositionCloseCalculator {

    private static final int SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BondPositionCloseCalculator() {
    }

    public record Result(BigDecimal buyValue, BigDecimal closeValue, BigDecimal totalPnl, BigDecimal returnPercent) {}

    public static Result compute(
            ManualBondPosition position,
            BigDecimal closePrice,
            BigDecimal collectedCoupon,
            BigDecimal fee
    ) {
        BigDecimal nominal = position.getNominalValue();
        BigDecimal buyPrice = position.getBuyPrice();
        if (nominal == null || buyPrice == null || closePrice == null) {
            return new Result(null, null, null, null);
        }
        BigDecimal buyValue = nominal.multiply(buyPrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal closeValue = nominal.multiply(closePrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal coupon = collectedCoupon != null && collectedCoupon.signum() > 0 ? collectedCoupon : BigDecimal.ZERO;
        BigDecimal safeFee = fee != null && fee.signum() > 0 ? fee : BigDecimal.ZERO;
        BigDecimal totalPnl = closeValue.add(coupon).subtract(buyValue).subtract(safeFee).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal returnPct = null;
        if (buyValue.signum() > 0) {
            returnPct = totalPnl.multiply(HUNDRED).divide(buyValue, 4, RoundingMode.HALF_UP);
        }
        return new Result(buyValue, closeValue, totalPnl, returnPct);
    }
}
