package com.nurseli.marketdata.api.dto.eurobond;

import java.util.Map;

public record EurobondBatchHistoryResponse(
        boolean available,
        String frequencyLabel,
        String unitLabel,
        String sourceLabel,
        Map<String, EurobondHistorySeriesDto> series) {}
