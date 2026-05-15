package com.nurseli.marketdata.domain.loan;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TL kredi faizi (EVDS akım, haftalık) alt türü — getiri/yield değil, borçlanma maliyeti göstergesi.
 */
public enum LoanRateSubtype {
    CONSUMER_TRY,
    VEHICLE_TRY,
    HOUSING_TRY,
    COMMERCIAL_TRY;

    public static Optional<LoanRateSubtype> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** Virgülle ayrılmış liste; boş veya null ise tüm alt türler. */
    public static Set<LoanRateSubtype> parseMany(String csv) {
        if (csv == null || csv.isBlank()) {
            return Arrays.stream(values()).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        LinkedHashSet<LoanRateSubtype> out = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            parse(part).ifPresent(out::add);
        }
        return out.isEmpty() ? Arrays.stream(values()).collect(Collectors.toCollection(LinkedHashSet::new)) : out;
    }
}
