package com.nurseli.marketdata.api.dto.loan;

import java.util.List;

public record LoanRatesHistoryResponse(
        String source,
        String frequency,
        String unit,
        List<LoanRateHistorySeriesDto> series
) {}
