package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockOverviewDto(
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp,
        BigDecimal marketCap,
        String marketCapSource,
        LocalDateTime marketCapAsOf
) {}
