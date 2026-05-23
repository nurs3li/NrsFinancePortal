package com.nurseli.nrsfinanceportal.application.pricealert;

import java.math.BigDecimal;

/**
 * finance-service price alert market snapshot kaydı — değerlendirme anındaki fiyat ve değişim metriklerini taşır.
 */

public record PriceAlertMarketSnapshot(
        BigDecimal priceTry,
        BigDecimal changePct,
boolean dataAvailable
) {
    /**
     * {@code unavailable} — Fiyat alınamadığında kullanılan boş/unavailable snapshot örneği döner.
     */
    public static PriceAlertMarketSnapshot unavailable() {
        return new PriceAlertMarketSnapshot(null, null, false);
    }
    }
