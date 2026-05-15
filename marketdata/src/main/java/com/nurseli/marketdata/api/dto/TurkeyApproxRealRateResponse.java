package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kabaca reel oran: nominal yüzde eksi TÜFE yıllık yüzdesi.
 * Nominal için önce {@code POLICY_RATE_TR} serisi denenir; yoksa
 * {@code TCMB_WEIGHTED_AVG_FUNDING_COST_TR} kullanılır — hangi mantıksal göstergenin kullanıldığı
 * {@code sourceIndicatorCode} ile açıkça döner.
 */
public record TurkeyApproxRealRateResponse(
        String sourceIndicatorCode,
        String sourceEvdsSeriesCode,
        BigDecimal nominalPercent,
        LocalDateTime nominalAsOf,
        BigDecimal cpiTrAnnualPercent,
        LocalDate cpiIndexMonth,
        BigDecimal approximateRealRatePercent,
        String methodologyNote
) {}
