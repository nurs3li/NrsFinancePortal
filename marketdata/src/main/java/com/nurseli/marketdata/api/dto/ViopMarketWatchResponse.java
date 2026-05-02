package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ViopMarketWatchResponse(
        List<Leader> topGainers,
        List<Leader> volumeLeaders,
        List<Leader> openInterestLeaders,
        LocalDateTime computedAt
) {
    public record Leader(
            String contractCode,
            BigDecimal price,
            BigDecimal annualizedBasisPct,
            Long openInterest,
            Long dailyVolume,
            String source,
            String quality
    ) {}
}

