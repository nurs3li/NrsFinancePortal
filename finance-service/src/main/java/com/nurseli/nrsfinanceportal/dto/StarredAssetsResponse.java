package com.nurseli.nrsfinanceportal.dto;

import java.util.List;

public record StarredAssetsResponse(
        int maxItems,
        List<StarredAssetSelectionDto> selected,
        List<StarredAssetResolvedDto> resolved
) {}
