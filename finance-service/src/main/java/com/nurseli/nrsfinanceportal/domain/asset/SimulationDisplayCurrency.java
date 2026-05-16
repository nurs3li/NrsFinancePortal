package com.nurseli.nrsfinanceportal.domain.asset;

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
