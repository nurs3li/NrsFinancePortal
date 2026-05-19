package com.nurseli.nrsfinanceportal.service.pricealert;

import java.math.BigDecimal;

public record PriceAlertMarketSnapshot(
        BigDecimal priceTry,
        BigDecimal changePct,
        boolean dataAvailable
) {
    public static PriceAlertMarketSnapshot unavailable() {
        return new PriceAlertMarketSnapshot(null, null, false);
    }
}
