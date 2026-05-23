package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Portföy özet metrikleri; toplam TRY değeri ve varlık tipine göre dağılım.
 */
public record PortfolioSummary(
        BigDecimal totalTry,
        Map<AssetType, BigDecimal> distribution
) {}
