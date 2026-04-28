package com.nurseli.marketdata.api.dto;

public record DebtInstrumentResponse(
        String isin,
        String name,
        String issuer,
        String maturityDate
) {}
