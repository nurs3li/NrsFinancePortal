package com.nurseli.nrsfinanceportal.common.dto;

import java.math.BigDecimal;

public record PortfolioConcentrationRiskDto(
        String topAssetSymbol,
        BigDecimal topAssetWeightPct,
        BigDecimal top3WeightPct,
        String riskLevel,
        String message
) {}
