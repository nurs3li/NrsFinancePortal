package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketPriceLatestResponse(
        String symbol,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp,
        LocalDateTime asOf,
        PriceQuality quality,
        BigDecimal marketCap,
        String marketCapSource,
        LocalDateTime marketCapAsOf
) {}