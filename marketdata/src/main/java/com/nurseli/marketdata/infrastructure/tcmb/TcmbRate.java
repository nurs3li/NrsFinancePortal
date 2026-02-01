package com.nurseli.marketdata.infrastructure.tcmb;

import java.math.BigDecimal;

public record TcmbRate(
        String symbol,
        BigDecimal buy,
        BigDecimal sell
) {}