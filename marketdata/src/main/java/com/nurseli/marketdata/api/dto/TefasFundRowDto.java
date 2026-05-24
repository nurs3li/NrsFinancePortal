package com.nurseli.marketdata.api.dto;

public record TefasFundRowDto(
        String code,
        String title,
        String fundType,
        Integer riskLevel,
        boolean tefasListed,
        Double price,
        Double return1d,
        Double return1w,
        Double return1m,
        Double return3m,
        Double return6m,
        Double return1y,
        Double returnYtd,
        Double return3y,
        Double return5y,
        String source) {}
