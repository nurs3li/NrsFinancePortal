package com.nurseli.nrsfinanceportal.application.bond;

import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * finance-service bond metrik hesaplayıcı — tahvil pozisyonu için değer, kupon, K/Z ve vade metriklerini üretir.
 */
@Component

public class BondPositionMetricsCalculator {

    private static final int SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final double DAYS_PER_MONTH = 30.4375;
    /**
     * Metrics — Bond pozisyon metrik DTO'su — değer, kupon, K/Z ve vade alanlarını taşır.
     */
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

    /**
     * {@code compute} — Pozisyon için alış/güncel değer, fiyat K/Z, kupon geliri, toplam getiri ve vadeye kalan gün metriklerini hesaplar (EVDS snapshot destekli overload dahil).
     */
    public Metrics compute(ManualBondPosition position, LocalDate today) {
        return compute(position, today, null);
    }

    /**
     * {@code compute} — Pozisyon için alış/güncel değer, fiyat K/Z, kupon geliri, toplam getiri ve vadeye kalan gün metriklerini hesaplar (EVDS snapshot destekli overload dahil).
     */
    public Metrics compute(ManualBondPosition position, LocalDate today, MarketDataClient.DebtLatestRow evds) {
        if (position == null) {
            return emptyMetrics(null);
        }
        BigDecimal currentPrice = position.getCurrentPrice();
    BigDecimal couponRate = position.getCouponRate();
        CouponFrequency couponFrequency = position.getCouponFrequency() != null
                ? position.getCouponFrequency()
                : CouponFrequency.NONE;

        if (evds != null) {
            if (BondMarketPriceSupport.isPlausibleMarketPrice(evds.dirtyPrice())) {
                currentPrice = evds.dirtyPrice();
            }
            if ((couponRate == null || couponRate.signum() <= 0)
                    && evds.couponRate() != null
                    && evds.couponRate().signum() > 0) {
                couponRate = evds.couponRate();
                couponFrequency = frequencyFromPerYear(evds.couponFrequencyPerYear());
            }
        }

        return computeWithTerms(position, today, currentPrice, couponRate, couponFrequency);
    }

    private Metrics computeWithTerms(
            ManualBondPosition position,
            LocalDate today,
            BigDecimal currentPrice,
            BigDecimal couponRate,
            CouponFrequency couponFrequency
    ) {
        BigDecimal nominal = position.getNominalValue();
        BigDecimal buyPrice = position.getBuyPrice();
        if (nominal == null || buyPrice == null) {
            return emptyMetrics(daysToMaturity(position, today));
        }

        BigDecimal buyValue = nominal.multiply(buyPrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal currentValue = currentPrice != null
                ? nominal.multiply(currentPrice).divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal pricePnl = currentValue != null ? currentValue.subtract(buyValue) : null;

        CouponFrequency freq = effectiveCouponFrequency(couponRate, couponFrequency);
        boolean hasCoupon = couponRate != null && couponRate.signum() > 0 && freq != CouponFrequency.NONE;

        BigDecimal annualCoupon = null;
        BigDecimal periodicCoupon = null;
        int completedPeriods = 0;
        BigDecimal collectedCoupon = BigDecimal.ZERO;
        BigDecimal estimatedAccrued = null;

        if (hasCoupon) {
            annualCoupon = nominal.multiply(couponRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
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

    private static CouponFrequency effectiveCouponFrequency(BigDecimal rate, CouponFrequency freq) {
        if (rate == null || rate.signum() <= 0) {
            return freq != null ? freq : CouponFrequency.NONE;
        }
        if (freq == null || freq == CouponFrequency.NONE) {
            return CouponFrequency.SEMI_ANNUAL;
        }
        return freq;
    }

    private static CouponFrequency frequencyFromPerYear(Integer perYear) {
        if (perYear == null || perYear < 1) {
            return CouponFrequency.SEMI_ANNUAL;
        }
        if (perYear >= 4) {
            return CouponFrequency.QUARTERLY;
        }
        if (perYear >= 2) {
            return CouponFrequency.SEMI_ANNUAL;
        }
        return CouponFrequency.ANNUAL;
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
