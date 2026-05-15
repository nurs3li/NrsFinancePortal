package com.nurseli.marketdata.domain.inflation;

import java.util.Optional;

public enum InflationIndicatorType {
    CPI,
    PPI;

    public static Optional<InflationIndicatorType> fromApi(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
