package com.nurseli.marketdata.application.debt;

import java.util.regex.Pattern;

/**
 * EVDS seri kodundan kupon ödeme sıklığı çıkarımı (resmi takvim değil).
 * Ör. 24D2, 61T2K… → yılda 2 → "6 ayda bir".
 */
public final class EvdsCouponFrequencyHeuristic {

    public static final String SOURCE = "EVDS_CODE_HEURISTIC";

    private static final Pattern D4 = Pattern.compile("D4(?![0-9])");
    private static final Pattern T4 = Pattern.compile("T4(?![0-9])");
    private static final Pattern D2 = Pattern.compile("D2(?![0-9])");
    private static final Pattern T2 = Pattern.compile("T2(?![0-9])");
    private static final Pattern D1 = Pattern.compile("D1(?![0-9])");
    private static final Pattern T1 = Pattern.compile("T1(?![0-9])");

    private EvdsCouponFrequencyHeuristic() {}

    public record Result(int perYear, String label, String source) {}

    public static Result resolve(String seriesCode) {
        if (seriesCode == null || seriesCode.isBlank()) {
            return null;
        }
        String code = seriesCode.trim().toUpperCase();
        if (matches(D4, code) || matches(T4, code)) {
            return new Result(4, "3 ayda bir", SOURCE);
        }
        if (matches(D2, code) || matches(T2, code)) {
            return new Result(2, "6 ayda bir", SOURCE);
        }
        if (matches(D1, code) || matches(T1, code)) {
            return new Result(1, "Yılda bir", SOURCE);
        }
        return null;
    }

    private static boolean matches(Pattern pattern, String code) {
        return pattern.matcher(code).find();
    }
}
