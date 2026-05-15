package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BistEquityHistoryResponse(
        String symbol,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal adjustedClose,
        BigDecimal rawClose,
        BigDecimal usdTry,
        BigDecimal bist100Value,
        BigDecimal marketCapTry,
        String source,
        String dataQuality) {}
