package com.nurseli.marketdata.infrastructure.evds;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** EVDS zaman serisi tek gözlem (örn. aylık TÜFE endeks seviyesi). */
public record EvdsSeriesPoint(LocalDateTime asOf, BigDecimal value) {}
