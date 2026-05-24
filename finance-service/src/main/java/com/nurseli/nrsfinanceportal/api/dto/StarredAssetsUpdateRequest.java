package com.nurseli.nrsfinanceportal.api.dto;

import java.util.List;

/**
 * Yıldızlı varlıklar güncelleme request'i; yeni seçim listesini taşır.
 */
public record StarredAssetsUpdateRequest(
        List<StarredAssetSelectionRequest> selected
) {}
