package com.nurseli.marketdata.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SpreadCalculator {

    /** Tek fiyat veren kaynaklar için spread: %0,2 (0,002) */
    private static final BigDecimal SPREAD_HALF = new BigDecimal("0.001");

    /**
     * Tek fiyat (mid) verildiğinde alış ve satış hesapla.
     * Satış (müşteriye satış) = mid * (1 + spread/2)
     * Alış (müşteriden alış) = mid * (1 - spread/2)
     */
    public static BigDecimal sellPrice(BigDecimal midPrice) {
        return midPrice.multiply(BigDecimal.ONE.add(SPREAD_HALF)).setScale(6, RoundingMode.HALF_UP);
    }

    public static BigDecimal buyPrice(BigDecimal midPrice) {
        return midPrice.multiply(BigDecimal.ONE.subtract(SPREAD_HALF)).setScale(6, RoundingMode.HALF_UP);
    }
}