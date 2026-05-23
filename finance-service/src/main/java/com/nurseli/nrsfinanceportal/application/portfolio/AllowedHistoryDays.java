package com.nurseli.nrsfinanceportal.application.portfolio;

/**
 * finance-service izin verilen geçmiş gün sabitleri — market-data history penceresi seçiminde kullanılan gün dilimlerini tanımlar.
 */
public final class AllowedHistoryDays {

    public static final int[] ORDERED = {
5, 7, 30, 90, 180, 365, 730,
            1095, 1460, 1825, 2190, 2555, 2920, 3285, 3650,
            4000, 4500, 5000, 5500, 6000, 6500, 7000, 7500, 8000,
    };

    private AllowedHistoryDays() {
    }

    /**
     * {@code smallestCovering} — Gerekli gün aralığını kapsayan en küçük izin verilen history penceresini seçer.
     */
    public static int smallestCovering(long requiredSpan) {
        long span = Math.max(1, requiredSpan);
        for (int d : ORDERED) {
    if (d >= span) {
                return d;
            }
        }
        return ORDERED[ORDERED.length - 1];
    }
}
