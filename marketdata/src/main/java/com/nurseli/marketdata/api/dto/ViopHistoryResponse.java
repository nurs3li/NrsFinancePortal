package com.nurseli.marketdata.api.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record ViopHistoryResponse(
        String contractCode,
        String underlying,
        String displayName,
        String contractName,
        int maturityMonth,
        int maturityYear,
        String assetClass,
        String segment,
        String sourceLabel,
        int delayMinutes,
        int periodMinutes,
        String chartType,
        String dataQuality,
        OffsetDateTime providerTimestamp,
        LocalDateTime from,
        LocalDateTime to,
        List<ViopPricePoint> points
) {}
