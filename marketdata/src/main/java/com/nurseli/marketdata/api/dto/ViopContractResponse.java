package com.nurseli.marketdata.api.dto;

public record ViopContractResponse(
        String contractCode,
        String underlying,
        String expiry,
        String type
) {}
