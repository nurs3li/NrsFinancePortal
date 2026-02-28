package com.nurseli.marketdata.api.dto;

import java.util.List;
import java.util.Map;

public record BatchHistoryResponse(
        Map<String, List<CandlePointResponse>> series
) {}