package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Market data güncel fiyat DTO.
 */
/** market-data-service {@code MarketPriceLatestResponse} ile uyumlu (döviz dahil). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketPriceLatestDto(
        String symbol,
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp,
        LocalDateTime asOf,
        String quality,
        BigDecimal marketCap,
        String marketCapSource,
        LocalDateTime marketCapAsOf
) {}