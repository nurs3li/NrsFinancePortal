package com.nurseli.nrsfinanceportal.dto;

public record StarredAssetResolvedDto(
        String marketType,
        String symbol,
        int position,
        boolean defaultFilled
) {}
