package com.nurseli.marketdata.api.dto.fx;

import java.time.Instant;
import java.util.List;

/** EVDS günlük döviz + efektif kur özeti; heatmap fiyat akışından bağımsız metadata. */
public record FxEffectiveRatesResponseDto(
        String generatedAt,
        String source,
        String frequency,
        List<FxEffectiveRateRowDto> rates,
        List<String> notes
) {
    public static FxEffectiveRatesResponseDto empty() {
        return new FxEffectiveRatesResponseDto(
                Instant.now().toString(),
                "EVDS",
                "DAILY",
                List.of(),
                List.of());
    }
}
