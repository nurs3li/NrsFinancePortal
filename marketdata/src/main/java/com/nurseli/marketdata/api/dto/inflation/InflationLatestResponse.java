package com.nurseli.marketdata.api.dto.inflation;

public record InflationLatestResponse(
        InflationIndicatorSnapshotDto cpi,
        InflationIndicatorSnapshotDto ppi,
        String methodologyNote,
        String sourceNote
) {}
