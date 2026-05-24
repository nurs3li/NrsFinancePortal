package com.nurseli.nrsfinanceportal.application.viop;

import java.math.BigDecimal;

/**
 * finance-service VIOP FX kurları kaydı — USDTRY ve EURTRY kurlarını VIOP TRY dönüşümünde taşır.
 */

public record ViopFxRates(BigDecimal usdTry, BigDecimal eurTry) {

    /**
     * {@code empty} — Kur bilgisi olmayan boş FX snapshot'ı döner.
     */
    public static ViopFxRates empty() {
        return new ViopFxRates(null, null);
    }

    /**
     * {@code rateFor} — Belirtilen ViopQuoteCurrency için ilgili kur değerini döner.
     */
    public BigDecimal rateFor(ViopQuoteCurrency currency) {
        return switch (currency) {
            case USD -> usdTry;
            case EUR -> eurTry != null && eurTry.signum() > 0 ? eurTry : usdTry;
    case TRY -> BigDecimal.ONE;
        };
    }

    /**
     * {@code isMissing} — Belirtilen para birimi için geçerli kur olup olmadığını kontrol eder.
     */
    public boolean isMissing(ViopQuoteCurrency currency) {
        if (currency == ViopQuoteCurrency.TRY) {
            return false;
    }
        BigDecimal r = rateFor(currency);
        return r == null || r.signum() <= 0;
    }
}
