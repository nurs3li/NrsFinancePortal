package com.nurseli.marketdata.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BistBackfillItemResponse(
        String symbol,
        BistBackfillStatus status,
        int rowsFetched,
        int rowsWritten,
        int rowsSkipped,
        List<String> warnings) {}
