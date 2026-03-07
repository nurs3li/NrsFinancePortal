package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;

public record StockOverviewDto(
        BigDecimal price,
        String source
) {}