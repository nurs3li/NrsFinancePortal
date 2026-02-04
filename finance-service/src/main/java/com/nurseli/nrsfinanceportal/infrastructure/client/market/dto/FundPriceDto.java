package com.nurseli.nrsfinanceportal.infrastructure.client.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundPriceDto(
        String fundCode,
        BigDecimal price,
        LocalDate date
) {}