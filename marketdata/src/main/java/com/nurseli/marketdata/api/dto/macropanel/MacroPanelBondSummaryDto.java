package com.nurseli.marketdata.api.dto.macropanel;

/**
 * Devlet tahvili — fiyat / liste oranı; YTM/yield iddiası yok.
 * {@code listedOranPercentNotYtm}: API alanındaki {@code yieldPct} kaynağından; YTM değildir.
 */
public record MacroPanelBondSummaryDto(
        String isin,
        Double indicativeDirtyPriceTry,
        Double listedOranPercentNotYtm,
        Long daysToMaturity,
        String category,
        String dirtyPriceUnit,
        Boolean isYield,
        String metricType,
        Boolean hasStructuredYieldData
) {}
