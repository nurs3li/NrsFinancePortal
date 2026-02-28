package com.nurseli.marketdata.api.dto;

import com.nurseli.marketdata.api.exception.InvalidRequestException;

public enum MarketType {
    FX,
    CRYPTO,
    METALS,
    FUNDS;

    public static MarketType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("type zorunludur. Geçerli değerler: FX, CRYPTO, METALS, FUNDS");
        }
        try {
            return MarketType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Geçersiz type: " + raw + ". Geçerli değerler: FX, CRYPTO, METALS, FUNDS");
        }
    }
}