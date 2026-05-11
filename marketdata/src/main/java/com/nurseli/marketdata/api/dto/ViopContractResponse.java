package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;

public record ViopContractResponse(
        String contractCode,
        String underlying,
        String expiry,
        String type,
        BigDecimal listPctChange1d,
        BigDecimal listPctChange7d,
        BigDecimal listPctChange30d,
        BigDecimal listPctChange365d,
        BigDecimal seqMovePct,
        String seqMoveTrend
) {}
