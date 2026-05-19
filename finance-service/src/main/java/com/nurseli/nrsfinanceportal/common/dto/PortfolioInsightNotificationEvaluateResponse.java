package com.nurseli.nrsfinanceportal.common.dto;

import java.util.List;

public record PortfolioInsightNotificationEvaluateResponse(
        int generatedCount,
        List<String> generatedTypes
) {}
