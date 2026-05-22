package com.nurseli.marketdata.infrastructure.dovizborsa;

import java.math.BigDecimal;

public record DovizborsaParsedRate(
        String bankCode,
        String bankLabel,
        String currency,
        BigDecimal buy,
        BigDecimal sell,
        BigDecimal changePct,
        String quoteTimeText,
        String trend) {}
