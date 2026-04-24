package com.nurseli.nrsfinanceportal.dto;

/**
 * Isı haritasının hesap yöntemini frontend'e açıkça taşır.
 */
public record HeatmapMeta(
        String equityMode,
        String equityChangeHorizon,
        String equityWeightMode,
        String multiAssetMode,
        String multiAssetChangeHorizon,
        String multiAssetWeightMode
) {}
