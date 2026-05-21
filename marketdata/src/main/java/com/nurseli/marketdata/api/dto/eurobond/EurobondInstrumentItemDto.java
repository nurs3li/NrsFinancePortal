package com.nurseli.marketdata.api.dto.eurobond;

import java.math.BigDecimal;

public record EurobondInstrumentItemDto(
        String isin,
        String name,
        String issuer,
        String currency,
        BigDecimal couponPct,
        String maturityDate,
        Long minLotUsd) {}
