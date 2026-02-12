package com.nurseli.whaleanalytics.domain;

import java.math.BigDecimal;

public record TrendResult(
        TrendDirection direction,
        BigDecimal velocity,    // ortalama değişim hızı (amount / minute)
        BigDecimal volatility   // dalgalanma seviyesi (0..1 arası normalize)
) {}
