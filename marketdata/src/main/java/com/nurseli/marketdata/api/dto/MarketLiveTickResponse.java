package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;

public record MarketLiveTickResponse(
        String category,
        String symbol,
        BigDecimal price,
        BigDecimal changePercent,
        BigDecimal volume
) {}
