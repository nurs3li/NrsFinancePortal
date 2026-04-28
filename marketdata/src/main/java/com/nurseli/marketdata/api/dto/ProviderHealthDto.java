package com.nurseli.marketdata.api.dto;

import java.time.LocalDateTime;

public record ProviderHealthDto(
        String provider,
        LocalDateTime lastSuccessAt,
        long failureCount,
        long freshnessLagSeconds
) {}
