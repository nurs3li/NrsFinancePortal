package com.nurseli.nrsfinanceportal.service.bond;

import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
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
    private static final double DAYS_PER_MONTH = 30.4375;

    public record Metrics(
            BigDecimal buyValue,
            BigDecimal currentValue,
            BigDecimal pricePnl,
            BigDecimal returnPct,
            BigDecimal annualCoupon,
            BigDecimal periodicCoupon,
            int completedCouponPeriods,
            BigDecimal collectedCoupon,
            BigDecimal estimatedAccruedCoupon,
            BigDecimal totalReturn,
            BigDecimal totalReturnPercent,
            Integer daysToMaturity
    ) {}

    public Metrics compute(ManualBondPosition position, LocalDate today) {
        if (position == null) {
            return emptyMetrics(null);
        }
        BigDecimal nominal = position.getNominalValue();
        BigDecimal buyPrice = position.getBuyPrice();
        if (nominal == null || buyPrice == null) {
            return emptyMetrics(daysToMaturity(position, today));
        }

        BigDecimal buyValue = nominal.multiply(buyPrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal currentPrice = position.getCurrentPrice();
        BigDecimal currentValue = currentPrice != null
                ? nominal.multiply(currentPrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal pricePnl = currentValue != null ? currentValue.subtract(buyValue) : null;

        CouponFrequency freq = position.getCouponFrequency() != null
                ? position.getCouponFrequency()
                : CouponFrequency.NONE;
        BigDecimal rate = position.getCouponRate();
        boolean hasCoupon = rate != null && rate.signum() > 0 && freq != CouponFrequency.NONE;

        BigDecimal annualCoupon = null;
        BigDecimal periodicCoupon = null;
        int completedPeriods = 0;
        BigDecimal collectedCoupon = BigDecimal.ZERO;
        BigDecimal estimatedAccrued = null;

        if (hasCoupon) {
            annualCoupon = nominal.multiply(rate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            int periodMonths = couponPeriodMonths(freq);
            if (periodMonths > 0) {
                int paymentsPerYear = 12 / periodMonths;
                periodicCoupon = annualCoupon.divide(BigDecimal.valueOf(paymentsPerYear), SCALE, RoundingMode.HALF_UP);
                if (position.getBuyDate() != null && today != null) {
                    double holdingMonths = holdingMonths(position.getBuyDate(), today);
                    completedPeriods = (int) Math.floor(holdingMonths / periodMonths);
                    collectedCoupon = periodicCoupon.multiply(BigDecimal.valueOf(completedPeriods))
                            .setScale(SCALE, RoundingMode.HALF_UP);
                    double remainder = holdingMonths - (completedPeriods * (double) periodMonths);
                    if (remainder > 0) {
                        estimatedAccrued = periodicCoupon
                                .multiply(BigDecimal.valueOf(remainder / periodMonths))
                                .setScale(SCALE, RoundingMode.HALF_UP);
                    }
                }
            }
        }

        BigDecimal totalReturn = null;
        if (pricePnl != null) {
            totalReturn = pricePnl.add(collectedCoupon).setScale(SCALE, RoundingMode.HALF_UP);
        } else if (collectedCoupon.signum() > 0) {
            totalReturn = collectedCoupon;
        }

        BigDecimal totalReturnPercent = null;
        if (totalReturn != null && buyValue.signum() > 0) {
            totalReturnPercent = totalReturn.multiply(HUNDRED).divide(buyValue, 4, RoundingMode.HALF_UP);
        }

        BigDecimal returnPct = null;
        if (pricePnl != null && buyValue.signum() > 0) {
            returnPct = pricePnl.multiply(HUNDRED).divide(buyValue, 4, RoundingMode.HALF_UP);
        }

        return new Metrics(
                buyValue,
                currentValue,
                pricePnl,
                returnPct,
                annualCoupon,
                periodicCoupon,
                completedPeriods,
                collectedCoupon,
                estimatedAccrued,
                totalReturn,
                totalReturnPercent,
                daysToMaturity(position, today)
        );
    }

    private static Metrics emptyMetrics(Integer daysToMaturity) {
        return new Metrics(
                null, null, null, null, null, null, 0,
                BigDecimal.ZERO, null, null, null, daysToMaturity);
    }

    private static int couponPeriodMonths(CouponFrequency freq) {
        if (freq == null) {
            return 0;
        }
        return switch (freq) {
            case ANNUAL -> 12;
            case SEMI_ANNUAL -> 6;
            case QUARTERLY -> 3;
            case NONE -> 0;
        };
    }

    private static double holdingMonths(LocalDate buyDate, LocalDate today) {
        long days = ChronoUnit.DAYS.between(buyDate, today);
        return Math.max(0, days) / DAYS_PER_MONTH;
    }

    private static Integer daysToMaturity(ManualBondPosition position, LocalDate today) {
        if (position.getMaturityDate() == null || today == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, position.getMaturityDate());
    }
}
