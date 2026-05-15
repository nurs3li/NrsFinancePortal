package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PreciousMetalUsdOverviewRow(
        String symbol,
        String displayName,
        BigDecimal midPriceUsdPerOz,
        PreciousMetalUsdChanges changes,
        String source,
        String sourceLabel,
        String delayInfo,
        LocalDateTime asOf,
        String currency,
        String unit
) {}
