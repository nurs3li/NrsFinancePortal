package com.nurseli.nrsfinanceportal.dto;

import java.util.List;

public record MarketTerminalListItemDto(
        String symbol,
        String category,
        String equitySubmarket,
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
        String contractMonth,
        Double basis,
        Double marginRequirement) {}
