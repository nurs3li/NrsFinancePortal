package com.nurseli.nrsfinanceportal.dto;

public record StarredAssetSelectionDto(
        String marketType,
        String symbol,
        int position
) {}
