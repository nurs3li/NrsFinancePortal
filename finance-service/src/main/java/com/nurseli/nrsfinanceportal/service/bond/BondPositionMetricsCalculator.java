package com.nurseli.nrsfinanceportal.service.bond;

import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class BondPositionMetricsCalculator {

    private static final int SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record Metrics(
            BigDecimal buyValue,
            BigDecimal currentValue,
            BigDecimal pnl,
            BigDecimal returnPct,
            BigDecimal annualCoupon,
            Integer daysToMaturity
    ) {}

    public Metrics compute(ManualBondPosition position, LocalDate today) {
        if (position == null) {
            return new Metrics(null, null, null, null, null, null);
        }
        BigDecimal nominal = position.getNominalValue();
        BigDecimal buyPrice = position.getBuyPrice();
        if (nominal == null || buyPrice == null) {
            return new Metrics(null, null, null, null, null, daysToMaturity(position, today));
        }

        BigDecimal buyValue = nominal.multiply(buyPrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal currentPrice = position.getCurrentPrice();
        BigDecimal currentValue = currentPrice != null
                ? nominal.multiply(currentPrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal pnl = currentValue != null ? currentValue.subtract(buyValue) : null;
        BigDecimal returnPct = null;
        if (pnl != null && buyValue.signum() > 0) {
            returnPct = pnl.multiply(HUNDRED).divide(buyValue, 4, RoundingMode.HALF_UP);
        }
        BigDecimal annualCoupon = null;
        if (position.getCouponRate() != null) {
            annualCoupon = nominal.multiply(position.getCouponRate()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        }
        return new Metrics(buyValue, currentValue, pnl, returnPct, annualCoupon, daysToMaturity(position, today));
    }

    private static Integer daysToMaturity(ManualBondPosition position, LocalDate today) {
        if (position.getMaturityDate() == null || today == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, position.getMaturityDate());
    }
}
