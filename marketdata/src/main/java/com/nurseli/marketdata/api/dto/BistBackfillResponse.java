package com.nurseli.marketdata.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

public record BistBackfillResponse(
        int requestedSymbols,
        int successfulSymbols,
        int failedSymbols,
        int totalRowsFetched,
        int totalRowsWritten,
        int totalRowsSkipped,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate from,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate to,
        String provider,
        List<BistBackfillItemResponse> items) {}
