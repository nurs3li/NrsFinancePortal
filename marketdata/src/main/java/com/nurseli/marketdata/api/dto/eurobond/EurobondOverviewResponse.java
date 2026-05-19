package com.nurseli.marketdata.api.dto.eurobond;

import java.time.LocalDate;

public record EurobondOverviewResponse(
        boolean available,
        String frequencyLabel,
        String unitLabel,
        String sourceLabel,
        LocalDate asOfDate,
        EurobondLatestMetricsDto latest,
        EurobondShareMetricsDto shares) {}
