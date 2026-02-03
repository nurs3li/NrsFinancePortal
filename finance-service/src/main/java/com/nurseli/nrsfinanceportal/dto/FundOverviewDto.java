package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;

public record FundOverviewDto(
        BigDecimal price,
        String source
) {}