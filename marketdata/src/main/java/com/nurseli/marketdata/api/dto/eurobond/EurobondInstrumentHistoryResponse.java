package com.nurseli.marketdata.api.dto.eurobond;

import java.util.List;

public record EurobondInstrumentHistoryResponse(
        boolean available,
        String isin,
        String name,
        String currency,
        String frequencyLabel,
        String unitLabel,
        String sourceLabel,
        List<EurobondInstrumentHistoryPointDto> points) {}
