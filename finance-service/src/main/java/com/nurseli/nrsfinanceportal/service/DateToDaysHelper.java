package com.nurseli.nrsfinanceportal.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class DateToDaysHelper {

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