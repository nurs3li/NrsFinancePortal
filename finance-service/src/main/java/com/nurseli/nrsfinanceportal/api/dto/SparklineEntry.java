package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sparkline grafiği için örneklenmiş kapanış fiyatları DTO'su; varlık sınıfı, sembol ve fiyat listesini taşır.
 */
public record SparklineEntry(
        String assetClass,
        String symbol,
        List<BigDecimal> closes
) {}
