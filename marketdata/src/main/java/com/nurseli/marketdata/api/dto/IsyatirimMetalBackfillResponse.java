package com.nurseli.marketdata.api.dto;

import java.time.Instant;
import java.util.List;

public record IsyatirimMetalBackfillResponse(
        List<String> requestedSymbols,
        List<String> successfulSymbols,
        List<String> failedSymbols,
        long insertedCount,
        long skippedDuplicateCount,
        long ignoredOutOfRangeCount,
        Instant startedAt,
        Instant finishedAt,
        String source
) {}
