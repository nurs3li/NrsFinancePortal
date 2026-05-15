package com.nurseli.marketdata.infrastructure.bist;

import java.time.Instant;

/**
 * BIST HTTP provider hata bilgisi.
 */
public record BistProviderError(
        BistProviderSource source,
        String symbol,
        String message,
        String exceptionClass,
        Integer httpStatus,
        Instant occurredAt
) {
    public static BistProviderError of(
            BistProviderSource source,
            String symbol,
            String message,
            String exceptionClass,
            Integer httpStatus,
            Instant occurredAt) {
        return new BistProviderError(source, symbol, message, exceptionClass, httpStatus, occurredAt);
    }
}
