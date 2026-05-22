package com.nurseli.marketdata.api.dto.bankfx;

import java.math.BigDecimal;

public record BankFxRateRowDto(
        String bankCode,
        String bankName,
        String currency,
        BigDecimal buy,
        BigDecimal sell,
        BigDecimal changePct,
        String quoteTime,
        String trend) {}
