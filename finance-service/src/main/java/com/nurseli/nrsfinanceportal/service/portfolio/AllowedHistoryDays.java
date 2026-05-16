package com.nurseli.nrsfinanceportal.service.portfolio;

/**
 * Market-data ile uyumlu: izin verilen gün sayıları (equity/crypto vb. {@code days} parametresi).
 * "Tümü" portföy zaman serisi gibi uzun aralıklar için 730 üstü kademeler eklenmiştir.
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
     * {@code requiredSpan} takvim günü (dahil) kadar geriye giden pencereyi kapsayan en küçük izinli gün.
     * Üst sınırı aşan istekler en büyük izinli güne sıkıştırılır (market-data {@code days} üst sınırı).
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
