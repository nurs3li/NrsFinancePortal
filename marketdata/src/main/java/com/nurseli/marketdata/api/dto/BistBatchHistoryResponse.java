package com.nurseli.marketdata.api.dto;

import java.util.List;
import java.util.Map;

public record BistBatchHistoryResponse(Map<String, List<BistEquityHistoryResponse>> historiesBySymbol) {}
