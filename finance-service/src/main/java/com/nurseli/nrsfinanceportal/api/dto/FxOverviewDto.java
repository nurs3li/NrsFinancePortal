package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;

/**
 * Döviz (FX) piyasa özeti DTO'su; alış/satış kuru ve veri kaynağını taşır.
 */
public record FxOverviewDto(
        BigDecimal buy,
        BigDecimal sell,
        String source
) {}