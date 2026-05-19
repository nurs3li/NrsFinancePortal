package com.nurseli.marketdata.api.dto.eurobond;

import java.util.List;

public record EurobondHistoryResponse(
        boolean available,
        String seriesCode,
        String label,
        String frequencyLabel,
        String unitLabel,
        String sourceLabel,
        List<EurobondHistoryPointDto> points) {}
