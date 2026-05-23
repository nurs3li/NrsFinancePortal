package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hisse piyasa özeti DTO'su; alış/satış fiyatı, piyasa değeri ve zaman damgasını taşır.
 */
public record StockOverviewDto(
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp,
        BigDecimal marketCap,
        String marketCapSource,
        LocalDateTime marketCapAsOf
) {}
