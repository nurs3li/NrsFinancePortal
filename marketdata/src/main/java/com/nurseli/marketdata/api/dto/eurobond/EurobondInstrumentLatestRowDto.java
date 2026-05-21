package com.nurseli.marketdata.api.dto.eurobond;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EurobondInstrumentLatestRowDto(
        String isin,
        String name,
        String currency,
        BigDecimal couponPct,
        String maturityDate,
        BigDecimal cleanPrice,
        BigDecimal yieldPct,
        LocalDate asOfDate,
        String source) {}
