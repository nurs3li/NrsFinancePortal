package com.nurseli.marketdata.api.dto.bankfx;

import java.time.Instant;
import java.util.List;

public record BankRatesBoardResponseDto(
        String source,
        String currency,
        Instant fetchedAt,
        boolean stale,
        List<BankFxRateRowDto> rows,
        TcmbReferenceDto tcmb,
        String attribution,
        List<String> notes) {

    public static BankRatesBoardResponseDto empty(String currency) {
        return new BankRatesBoardResponseDto(
                "DOVIZBORSA",
                currency,
                null,
                true,
                List.of(),
                null,
                "Kaynak: dovizborsa.com",
                List.of("Henüz banka kuru verisi yok. Zamanlanmış görev sonrası tekrar deneyin."));
    }
}
