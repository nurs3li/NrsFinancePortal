package com.nurseli.nrsfinanceportal.service.portfolio;

/**
 * Market-data ile uyumlu: yalnızca bu sabit gün sayıları kabul edilir.
 */
public final class AllowedHistoryDays {

    public static final int[] ORDERED = {5, 7, 30, 90, 180, 365, 730};

    private AllowedHistoryDays() {
    }

    /**
     * {@code requiredSpan} takvim günü (dahil) kadar geriye giden pencereyi kapsayan en küçük izinli gün.
     * @return -1 eğer 730'u aşıyorsa
     */
    public static int smallestCovering(long requiredSpan) {
        long span = Math.max(1, requiredSpan);
        for (int d : ORDERED) {
            if (d >= span) {
                return d;
            }
        }
        return -1;
    }
}
