package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketPriceLatestDto(
        String symbol,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp
) {}