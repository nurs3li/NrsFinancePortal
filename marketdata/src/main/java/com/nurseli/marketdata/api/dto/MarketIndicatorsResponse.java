package com.nurseli.marketdata.api.dto;

import java.util.List;
import java.util.Map;

public record MarketIndicatorsResponse(
        String type,
        String symbol,
        int days,
        List<IndicatorPointResponse> close,
        Map<Integer, List<IndicatorPointResponse>> ma,
        TrendResponse trend
) {}