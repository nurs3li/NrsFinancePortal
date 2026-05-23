package com.nurseli.nrsfinanceportal.api.dto;

import java.util.List;

/**
 * Piyasa terminali liste satırı DTO'su; fiyat, değişim, hacim, vade/kupon ve fon metriklerini taşır.
 */
public record MarketTerminalListItemDto(
        String symbol,
        String category,
        String equitySubmarket,
        String fundSubmarket,
        String displayName,
        String name,
        double price,
        double changePercent,
        Double dailyChangePercent,
        String trend,
        Double volume,
        String currency,
        String marketRegion,
        String exchange,
        String sector,
        String source,
        Integer delayMinutes,
        List<Double> sparklineCloses,
        Double pctDay,
        Double pctWeek,
        Double pctMonth,
        Double pctYear,
        String maturityDate,
        Integer daysToMaturity,
        Double couponRate,
        Double yieldToMaturity,
        Integer couponFrequencyPerYear,
        String couponFrequencyLabel,
        String contractMonth,
        Double basis,
        Double marginRequirement,
        Integer fundRiskLevel,
        Double fundReturn3m,
        Double fundReturn6m,
        Double fundReturn3y,
        Double fundReturn5y) {}
