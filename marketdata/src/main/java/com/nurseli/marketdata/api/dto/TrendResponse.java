package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;

public record TrendResponse(
        String direction,          // UP / DOWN / FLAT
        BigDecimal slope,          // lineer regresyon eğimi (base=100 serisi üzerinde)
        BigDecimal normalizedReturn, // (last/first)-1
        BigDecimal strength        // 0..1
) {}