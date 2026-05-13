package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record CandlePointResponse(
        OffsetDateTime t,
        BigDecimal o,
        BigDecimal h,
        BigDecimal l,
        BigDecimal c,
        BigDecimal v
) {
    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    public static CandlePointResponse of(OffsetDateTime t, BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c, BigDecimal v) {
        return new CandlePointResponse(t, o, h, l, c, v);
    }

    /**
     * Takvim saati değerini İstanbul duvar saati olarak yorumlayıp tek anlık üretir (tick / saatlik sorgu).
     */
    public static CandlePointResponse atIstanbul(LocalDateTime wallClock, BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c, BigDecimal v) {
        return new CandlePointResponse(wallClock.atZone(IST).toOffsetDateTime(), o, h, l, c, v);
    }

    /** Günlük mum: iş günü tarihi → o günün İstanbul gece yarısı (offset ile). */
    public static CandlePointResponse atIstanbulMidnight(LocalDate date, BigDecimal o, BigDecimal h, BigDecimal l, BigDecimal c, BigDecimal v) {
        return new CandlePointResponse(date.atStartOfDay(IST).toOffsetDateTime(), o, h, l, c, v);
    }
}
