package com.nurseli.marketdata.api.dto.loan;

import java.time.LocalDate;
import java.util.List;

public record LoanRatesLatestResponse(
        String source,
        String frequency,
        String unit,
        LocalDate asOf,
        List<LoanRateLatestItemDto> items
) {}
