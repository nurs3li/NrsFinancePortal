package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Çözümlenmiş yıldızlı varlık DTO'su; piyasa tipi, sembol, sıra ve varsayılan doldurma bayrağını taşır.
 */
public record StarredAssetResolvedDto(
        String marketType,
        String symbol,
        int position,
        boolean defaultFilled
) {}
