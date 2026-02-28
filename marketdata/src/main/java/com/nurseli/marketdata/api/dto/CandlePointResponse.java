package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CandlePointResponse(
        LocalDateTime t,
        BigDecimal o,
        BigDecimal h,
        BigDecimal l,
        BigDecimal c,
        BigDecimal v
) {}