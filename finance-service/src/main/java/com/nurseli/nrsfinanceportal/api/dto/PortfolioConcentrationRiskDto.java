package com.nurseli.nrsfinanceportal.api.dto;

import java.math.BigDecimal;

/**
 * Portföy konsantrasyon riski DTO'su; en büyük varlık ağırlığı ve risk seviyesi mesajını taşır.
 */
public record PortfolioConcentrationRiskDto(
        String topAssetSymbol,
        BigDecimal topAssetWeightPct,
        BigDecimal top3WeightPct,
        String riskLevel,
        String message
) {}
