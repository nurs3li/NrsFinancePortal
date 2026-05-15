package com.nurseli.marketdata.api.dto.inflation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * EVDS aylık endeks serisinden türetilen metrikler.
 * {@code indexValue} endeks seviyesidir (enflasyon oranı değil);
 * {@code monthlyChangePercent} ve {@code annualChangePercent} yüzde olarak hesaplanır (yoksa null).
 */
public record InflationIndicatorSnapshotDto(
        String category,
        String indicatorType,
        String source,
        String frequency,
        String unit,
        Integer baseYear,
        String country,
        String seriesCode,
        LocalDate indexMonth,
        BigDecimal indexValue,
        BigDecimal monthlyChangePercent,
        BigDecimal annualChangePercent,
        String note
) {}
