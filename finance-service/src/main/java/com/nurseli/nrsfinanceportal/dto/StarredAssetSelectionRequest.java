package com.nurseli.nrsfinanceportal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StarredAssetSelectionRequest(
        String marketType,
        String symbol
) {}
