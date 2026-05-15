package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TÜFE genel endeks serisinden türetilen metrikler.
 * {@code cpiTrIndex} EVDS’teki endeks seviyesidir (yüzde değil);
 * {@code cpiTrMonthly} ve {@code cpiTrAnnual} yüzde olarak hesaplanır.
 */
public record CpiTrMacroResponse(
        String seriesCode,
        LocalDate indexMonth,
        BigDecimal cpiTrIndex,
        BigDecimal cpiTrMonthly,
        BigDecimal cpiTrAnnual,
        String annualNote
) {}
