package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BistEquityLatestResponse(
        String symbol,
        String displayName,
        String sector,
        String currency,
        BigDecimal adjustedClose,
        BigDecimal rawClose,
        BigDecimal change,
        BigDecimal changePercent,
        BigDecimal volume,
        BigDecimal marketCapTry,
        BigDecimal marketCapUsd,
        String source,
        String dataQuality,
        Instant lastUpdated) {}
