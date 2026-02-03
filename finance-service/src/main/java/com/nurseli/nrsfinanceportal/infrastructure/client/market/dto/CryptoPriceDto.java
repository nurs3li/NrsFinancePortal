package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CryptoPriceDto(
        BigDecimal price,
        String source,
        LocalDateTime timestamp
) {}