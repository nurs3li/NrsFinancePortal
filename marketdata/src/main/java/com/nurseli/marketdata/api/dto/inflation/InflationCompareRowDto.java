package com.nurseli.marketdata.api.dto.inflation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** TÜFE ve Yİ-ÜFE hizalanmış aylık yüzde değişimleri (grafikler için YoY öncelikli). */
public record InflationCompareRowDto(
        LocalDate month,
        BigDecimal cpiMonthlyChangePercent,
        BigDecimal cpiAnnualChangePercent,
        BigDecimal ppiMonthlyChangePercent,
        BigDecimal ppiAnnualChangePercent
) {}
