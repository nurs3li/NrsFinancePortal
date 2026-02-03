package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;

public record MetalOverviewDto(
        BigDecimal price,

        String source
) {}