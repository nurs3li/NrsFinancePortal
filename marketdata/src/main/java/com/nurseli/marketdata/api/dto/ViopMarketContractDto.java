package com.nurseli.marketdata.api.dto;

public record ViopMarketContractDto(
        String contractCode,
        String underlying,
        String displayName,
        String contractName,
        int maturityMonth,
        int maturityYear,
        String assetClass,
        String segment,
        String chartType,
        boolean enabled,
        boolean expired,
        String sourceLabel,
        int delayMinutes) {}
