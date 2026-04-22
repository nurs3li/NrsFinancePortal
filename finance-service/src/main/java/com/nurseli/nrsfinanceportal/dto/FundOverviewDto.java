package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FundOverviewDto(
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp
) {}
