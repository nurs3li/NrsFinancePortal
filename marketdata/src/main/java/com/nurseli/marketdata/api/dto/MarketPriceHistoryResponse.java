package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketPriceHistoryResponse(
        BigDecimal buyPrice,
        BigDecimal sellPrice,
        LocalDateTime timestamp
) {}