package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Volatilite grafiği satırı DTO'su; varlık sınıfı, sembol ve günlük getiri standart sapmasını taşır.
 */
public record VolatilityEntry(
        String assetClass,
        String symbol,
        double dailyVolatility
) {}
