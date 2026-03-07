package com.nurseli.nrsfinanceportal.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record MarketOverviewResponse(
        Map<String, FxOverviewDto> doviz,
        Map<String, MetalOverviewDto> metals,
        Map<String, CryptoOverviewDto> crypto,
        Map<String, FundOverviewDto> funds,
        Map<String, StockOverviewDto> stocks,
        LocalDateTime timestamp
) {}