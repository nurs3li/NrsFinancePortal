package com.nurseli.nrsfinanceportal.api.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Dashboard özet response'u; portföy, spot ve vadeli (VIOP/bond) segment özetlerini taşır.
 */
public record DashboardSummaryResponse(

        PortfolioSummary portfolio,
        BigDecimal totalPortfolioValueTry,
        /** Spot / klasik portföy (kripto, döviz, hisse, fon, metal). */
        TradingSegmentSummary spotTrading,
        /** VİOP + tahvil / eurobond vadeli segmenti. */
        TradingSegmentSummary futuresDerivatives

) {

    /**
     * Ticaret segmenti özeti; maliyet, PnL ve toplam değer (TRY).
     */
    public record TradingSegmentSummary(
            BigDecimal totalCostTry,
            BigDecimal totalPnlTry,
            BigDecimal totalValueTry
    ) {}

    /**
     * Portföy dağılım özeti; toplam değer, varlık tipi dağılımı ve kategori kırılımlarını taşır.
     */
    public record PortfolioSummary(
            BigDecimal totalValueTry,
            Map<AssetType, BigDecimal> distribution,
            BigDecimal totalCostTry,
            BigDecimal totalPnlTry,
            BigDecimal totalPnlPct,
            List<PortfolioCategoryBreakdown> categories
    ) {}

    /**
     * Tek varlık tipi kategori kırılımı; değer, maliyet ve PnL metriklerini taşır.
     */
    public record PortfolioCategoryBreakdown(
            AssetType assetType,
            BigDecimal valueTry,
            BigDecimal costTry,
            BigDecimal pnlTry,
            BigDecimal pnlPct
    ) {}
}
