package com.nurseli.nrsfinanceportal.api.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Piyasa genel bakış response'u; döviz, metal, kripto, fon ve hisse fiyatlarını sembol bazında taşır.
 */
public record MarketOverviewResponse(
        Map<String, FxOverviewDto> doviz,
        Map<String, MetalOverviewDto> metals,
        Map<String, CryptoOverviewDto> crypto,
        Map<String, FundOverviewDto> funds,
        Map<String, StockOverviewDto> stocks,
        LocalDateTime timestamp
) {}