package com.nurseli.nrsfinanceportal.dto;

/**
 * Sample standard deviation of daily simple returns (last N closes), as decimal (e.g. 0.015 = 1.5%).
 */
public record VolatilityEntry(
        String assetClass,
        String symbol,
        double dailyVolatility
) {}
