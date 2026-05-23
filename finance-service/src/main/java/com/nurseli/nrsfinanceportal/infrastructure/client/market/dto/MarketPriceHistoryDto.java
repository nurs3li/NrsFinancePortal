package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Market data geçmiş fiyat serisi DTO.
 */
public record MarketPriceHistoryDto(
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        LocalDateTime timestamp
) {}