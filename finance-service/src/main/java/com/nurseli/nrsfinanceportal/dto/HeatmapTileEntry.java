package com.nurseli.nrsfinanceportal.dto;

/**
 * Tek bir hücre: Finviz tarzı treemap içinde sektör altında sembol kutusu.
 */
public record HeatmapTileEntry(
        String sector,
        String symbol,
        String assetClass,
        double changePercent,
        double layoutWeight
) {}
