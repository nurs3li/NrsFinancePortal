package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ViopSnapshotResponse(
        String contractCode,
        String expiryDate,
        String contractMonth,
        BigDecimal price,
        BigDecimal theoreticalSpot,
        BigDecimal basis,
        BigDecimal annualizedBasisPct,
        BigDecimal marginRequirement,
        String longShortIndicator,
        Long openInterest,
        Long dailyVolume,
        String oiPriceRegime,
        String source,
        LocalDateTime asOf,
        Integer daysToExpiry,
        String dataQuality,
        String priceSource,
        Long priceLatencyMs,
        /** CSV import sonrası DB rollup; liste aralığına göre önceden hesaplanmış % (null = veri yok). */
        BigDecimal listPctChange1d,
        BigDecimal listPctChange7d,
        BigDecimal listPctChange30d,
        BigDecimal listPctChange365d,
        /**
         * Terminal listesi için: kontratın son asOf gününe kadar son 14 takvim günü, gün başına son pozitif kapanış;
         * ilk ve son gün fiyatına göre % (CSV rollup değil; latest() içinde hesaplanır).
         */
        BigDecimal listPctChange14d,
        /** Son iki CSV/DB snapshot arası %; liste ve trend için (takvim günü değil). */
        BigDecimal seqMovePct,
        String seqMoveTrend,
        /** Son N kapanış (pozitif fiyat); terminal listesi sparkline için tek istekte. */
        List<BigDecimal> sparklineCloses
) {}
