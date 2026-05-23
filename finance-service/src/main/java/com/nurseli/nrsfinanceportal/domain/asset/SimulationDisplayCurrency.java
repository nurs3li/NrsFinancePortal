package com.nurseli.nrsfinanceportal.domain.asset;

/**
 * Simülasyon ekranında gösterim para birimi (TRY, USD vb.).
 */
public enum SimulationDisplayCurrency {
    TRY,
    USD;

    public static SimulationDisplayCurrency parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TRY;
        }
        return SimulationDisplayCurrency.valueOf(raw.trim().toUpperCase());
    }
}
