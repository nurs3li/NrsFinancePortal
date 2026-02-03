package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MetalPriceDto(
        BigDecimal price,
        String unit,
        String source,
        LocalDateTime timestamp
) {}