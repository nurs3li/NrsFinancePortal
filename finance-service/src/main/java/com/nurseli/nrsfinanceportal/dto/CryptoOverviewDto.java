package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;

public record CryptoOverviewDto(
        BigDecimal price,
        String source
) {}