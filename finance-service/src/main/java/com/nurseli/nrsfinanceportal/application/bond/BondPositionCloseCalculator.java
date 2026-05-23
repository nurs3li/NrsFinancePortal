package com.nurseli.nrsfinanceportal.application.bond;

import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * finance-service bond kapanış hesaplayıcı — satılmış tahvil pozisyonu için kapanış K/Z ve getiri yüzdesini hesaplar.
 */
public final class BondPositionCloseCalculator {

    private static final int SCALE = 6;
private static final BigDecimal HUNDRED = new BigDecimal("100");

    private BondPositionCloseCalculator() {
    }

    /**
     * Result — Bond kapanış hesaplama sonucu — alış değeri, kapanış değeri, toplam K/Z ve getiri yüzdesini taşır.
     */
    public record Result(BigDecimal buyValue, BigDecimal closeValue, BigDecimal totalPnl, BigDecimal returnPercent) {}

    /**
     * {@code compute} — Alış değeri, kapanış değeri, toplanan kupon ve masraf ile toplam K/Z ve getiri yüzdesini hesaplar.
     */
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
