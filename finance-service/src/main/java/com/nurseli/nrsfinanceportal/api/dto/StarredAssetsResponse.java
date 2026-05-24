package com.nurseli.nrsfinanceportal.api.dto;

import java.util.List;

/**
 * Yıldızlı varlıklar response'u; limit, ham seçimler ve çözümlenmiş varlık listesini taşır.
 */
public record StarredAssetsResponse(
        int maxItems,
        List<StarredAssetSelectionDto> selected,
        List<StarredAssetResolvedDto> resolved
) {}
