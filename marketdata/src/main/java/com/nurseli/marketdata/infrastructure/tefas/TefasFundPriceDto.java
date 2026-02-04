package com.nurseli.marketdata.infrastructure.tefas;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TefasFundPriceDto(
        String fundCode,
        BigDecimal price,
        LocalDate date
) {}