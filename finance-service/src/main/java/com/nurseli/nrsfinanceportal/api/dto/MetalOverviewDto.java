package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Metal piyasa özeti DTO'su; alış/satış fiyatı, kaynak ve zaman damgasını taşır.
 */
public record MetalOverviewDto(
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        String source,
        LocalDateTime timestamp
) {}
