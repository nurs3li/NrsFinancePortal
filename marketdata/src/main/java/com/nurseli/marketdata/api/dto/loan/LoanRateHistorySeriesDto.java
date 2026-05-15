package com.nurseli.marketdata.api.dto.loan;

import java.util.List;

public record LoanRateHistorySeriesDto(
        String type,
        String label,
        String seriesCode,
        List<LoanRateHistoryPointDto> points
) {}
