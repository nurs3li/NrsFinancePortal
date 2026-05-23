package com.nurseli.nrsfinanceportal.application;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * finance-service tarih yardımcısı — LocalDate'i bugüne göre gün sayısına çevirir (1–3650 aralığında sınırlar).
 */
@Component

public class DateToDaysHelper {

    /**
     * {@code toDays} — Verilen tarihten bugüne kadar geçen gün sayısını hesaplar; null tarihte hata fırlatır, minimum 1 maksimum 3650 gün döner.
     */
    public int toDays(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }

        long raw = ChronoUnit.DAYS.between(date, LocalDate.now());

        if (raw < 1) return 1;
        if (raw > 3650) return 3650; // 10 yıl tavan (aşırı sorguyu sınırlar)

        return (int) raw;
    }
}