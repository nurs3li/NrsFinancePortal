package com.nurseli.marketdata.application.bankfx;

import com.nurseli.marketdata.api.dto.bankfx.BankFxRateRowDto;
import com.nurseli.marketdata.api.dto.bankfx.BankRatesBoardResponseDto;
import com.nurseli.marketdata.config.BankRatesProperties;
import com.nurseli.marketdata.domain.bankfx.BankFxLatest;
import com.nurseli.marketdata.infrastructure.persistence.BankFxLatestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BankRatesBoardService {

    private final BankRatesProperties properties;
    private final BankFxLatestRepository repository;

    public BankRatesBoardResponseDto load(String rawCurrency) {
        String currency = normalizeCurrency(rawCurrency);
        String source = BankRatesProperties.SOURCE_DOVIZBORSA;
        List<BankFxLatest> rows = repository.findBySourceAndCurrencyOrderByBankNameAsc(source, currency);
        Instant fetchedAt = repository.findMaxFetchedAtBySource(source).orElse(null);
        if (rows.isEmpty()) {
            return BankRatesBoardResponseDto.empty(currency);
        }
        boolean stale = isStale(fetchedAt);
        List<BankFxRateRowDto> dtoRows = rows.stream().map(this::toDto).toList();
        List<String> notes = List.of(
                "Veriler 2 saatte bir güncellenir; anlık banka gişe kuru değildir.",
                "Üçüncü taraf özet ekranıdır; işlem öncesi resmi banka veya TCMB kurunu esas alın.");
        return new BankRatesBoardResponseDto(
                source,
                currency,
                fetchedAt,
                stale,
                dtoRows,
                null,
                "Kaynak: dovizborsa.com",
                notes);
    }

    private boolean isStale(Instant fetchedAt) {
        if (fetchedAt == null) {
            return true;
        }
        Duration maxAge = Duration.ofHours(Math.max(1, properties.getStaleAfterHours()));
        return Duration.between(fetchedAt, Instant.now()).compareTo(maxAge) > 0;
    }

    private BankFxRateRowDto toDto(BankFxLatest e) {
        String trend = "FLAT";
        if (e.getChangePct() != null) {
            int cmp = e.getChangePct().signum();
            if (cmp > 0) {
                trend = "UP";
            } else if (cmp < 0) {
                trend = "DOWN";
            }
        }
        return new BankFxRateRowDto(
                e.getBankCode(),
                e.getBankName(),
                e.getCurrency(),
                e.getBuyPrice(),
                e.getSellPrice(),
                e.getChangePct(),
                e.getQuoteTimeText(),
                trend);
    }

    private static String normalizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return "USD";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
