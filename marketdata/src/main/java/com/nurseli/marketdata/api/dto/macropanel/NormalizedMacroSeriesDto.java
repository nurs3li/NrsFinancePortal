package com.nurseli.marketdata.api.dto.macropanel;

import java.util.List;

/**
 * Faiz &amp; Enflasyon paneli için ortak seri şeması (EVDS / tahvil birleşik görünüm).
 * {@code frequency}: DAILY | WEEKLY | MONTHLY — UI frekans uyarıları için.
 */
public record NormalizedMacroSeriesDto(
        String code,
        String label,
        String category,
        String frequency,
        String unit,
        String source,
        List<NormalizedMacroObservationDto> observations,
        /** EVDS mantıksal anahtar (örn. CPI_TR_INDEX); panel seçicileri için. */
        String logicalKey,
        /** Örn. USD / EUR — döviz mevduat serileri için. */
        String currency,
        /** Örn. 1M, 3M, 6M, 1Y — vade. */
        String tenor,
        /** Örn. FLOW — EVDS akım yüzdesi için isteğe bağlı meta. */
        String dataType
) {
    /** Geriye dönük uyumluluk: eski 8 bileşenli çağrılar. */
    public NormalizedMacroSeriesDto(
            String code,
            String label,
            String category,
            String frequency,
            String unit,
            String source,
            List<NormalizedMacroObservationDto> observations,
            String logicalKey
    ) {
        this(code, label, category, frequency, unit, source, observations, logicalKey, null, null, null);
    }
}
