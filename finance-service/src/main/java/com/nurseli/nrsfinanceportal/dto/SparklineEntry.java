package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Downsampled close prices for a compact sparkline (≈24 points).
 */
public record SparklineEntry(
        String assetClass,
        String symbol,
        List<BigDecimal> closes
) {}
