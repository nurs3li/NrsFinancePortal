package com.nurseli.marketdata.api.dto;

public record BistSymbolResponse(
        String symbol,
        String displayName,
        String sector,
        String exchange,
        String currency,
        String yahooSymbol,
        String assetType,
        String country) {}
