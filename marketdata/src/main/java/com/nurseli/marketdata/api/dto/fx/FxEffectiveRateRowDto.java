package com.nurseli.marketdata.api.dto.fx;

/**
 * TCMB/EVDS günlük döviz (kayıtlı) ve efektif (nakit) gösterge kurları — banka kuru değildir.
 * Alanlar eksik günlerde {@code null} olabilir.
 */
public record FxEffectiveRateRowDto(
        String currency,
        String date,
        Double fxBuying,
        Double fxSelling,
        Double cashBuying,
        Double cashSelling,
        Double fxSpread,
        Double cashSpread,
        Double cashVsFxBuyingDiff,
        Double cashVsFxSellingDiff
) {}
