package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Kullanıcının seçtiği yıldızlı varlık DTO'su; piyasa tipi, sembol ve sıra bilgisini taşır.
 */
public record StarredAssetSelectionDto(
        String marketType,
        String symbol,
        int position
) {}
