package com.nurseli.marketdata.api.dto.eurobond;

import java.util.List;

public record EurobondHistorySeriesDto(
        String seriesCode, String label, String seriesKey, List<EurobondHistoryPointDto> points) {}
