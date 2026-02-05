package com.nurseli.nrsfinanceportal.domain.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;
import java.util.Map;

public record PortfolioSummary(
        BigDecimal totalTry,
        Map<AssetType, BigDecimal> distribution
) {}
