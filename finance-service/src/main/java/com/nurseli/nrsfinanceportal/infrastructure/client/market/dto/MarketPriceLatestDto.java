package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** market-data-service {@code MarketPriceLatestResponse} ile aynı alanlar */
public record MarketPriceLatestDto(
        String symbol,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp,
        BigDecimal marketCap,
        String marketCapSource,
        LocalDateTime marketCapAsOf
) {}