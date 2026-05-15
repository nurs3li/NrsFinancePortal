package com.nurseli.marketdata.api.dto.macropanel;

/**
 * Normalize makro gözlem — tarih ISO-8601 {@code yyyy-MM-dd}, değer EVDS parse sonrası sayı.
 */
public record NormalizedMacroObservationDto(String date, Double value) {}
