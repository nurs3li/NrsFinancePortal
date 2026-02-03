package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;

public record FxOverviewDto(
        BigDecimal buy,
        BigDecimal sell,
        String source
) {}