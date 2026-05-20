package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DebtSnapshotResponse(
        String isin,
        BigDecimal dirtyPrice,
        BigDecimal yieldPct,
        String maturityDate,
        Long daysToMaturity,
        BigDecimal couponRate,
        String source,
        LocalDateTime asOf,
        String quality,
        Boolean synthetic,
        /** Yapılandırılmış YTM / simple / compound verisi yoksa {@code false}. */
        Boolean hasStructuredYieldData,
        /** Panel sınıflandırması — fiyat performansı bağlamı. */
        String bondDataCategory,
        /** Kirli fiyat birimi. */
        String dirtyPriceUnit,
        /** {@code yieldPct} alanı gerçek YTM değilse {@code false} (EVDS ORAN vb.). */
        Boolean yieldFieldRepresentsYtm,
        Integer couponFrequencyPerYear,
        String couponFrequencyLabel,
        String couponFrequencySource
) {}
