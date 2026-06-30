package com.nurseli.nrsfinanceportal.application.bond;

import java.math.BigDecimal;

/**
 * Tahvil piyasa fiyatı doğrulama — DİBS fiyatları 100 nominal üzerinden genelde ~50–150 aralığındadır.
 * EVDS TP_* serileri bazen kupon/getiri benzeri değerler döndürdüğü için düşük değerler reddedilir.
 */
public final class BondMarketPriceSupport {

    /** 100 nominal üzerinden makul alt sınır (iskontolu tahvil senaryosu). */
    public static final BigDecimal MIN_PLAUSIBLE_DIRTY_PRICE = new BigDecimal("50");

    private BondMarketPriceSupport() {
    }

    public static boolean isPlausibleMarketPrice(BigDecimal price) {
        return price != null
                && price.signum() > 0
                && price.compareTo(MIN_PLAUSIBLE_DIRTY_PRICE) >= 0;
    }

    public static boolean isPlausibleMarketPrice(double price) {
        return Double.isFinite(price) && price >= MIN_PLAUSIBLE_DIRTY_PRICE.doubleValue();
    }
}
