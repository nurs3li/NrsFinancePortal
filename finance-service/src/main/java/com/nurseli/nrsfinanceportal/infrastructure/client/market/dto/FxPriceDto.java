package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FxPriceDto(
        BigDecimal buy,
        BigDecimal sell,
        String source,
        LocalDateTime timestamp
) {}