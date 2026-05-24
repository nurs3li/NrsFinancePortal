package com.nurseli.nrsfinanceportal.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Yıldızlı varlık seçim request'i; piyasa tipi ve sembol bilgisini taşır.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StarredAssetSelectionRequest(
        String marketType,
        String symbol
) {}
