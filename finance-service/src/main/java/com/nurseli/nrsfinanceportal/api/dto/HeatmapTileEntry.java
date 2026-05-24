package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Tek bir hücre: Finviz tarzı treemap içinde sektör altında sembol kutusu.
 */
public record HeatmapTileEntry(
        String sector,
        String industry,
        String symbol,
        String assetClass,
        double changePercent,
        double layoutWeight,
        String mode,
        String changeHorizon,
        String weightMode,
        String marketCapSource,
        java.time.LocalDateTime marketCapAsOf
) {}
