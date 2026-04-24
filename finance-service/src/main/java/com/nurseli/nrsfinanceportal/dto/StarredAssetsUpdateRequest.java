package com.nurseli.nrsfinanceportal.dto;

import java.util.List;

public record StarredAssetsUpdateRequest(
        List<StarredAssetSelectionRequest> selected
) {}
