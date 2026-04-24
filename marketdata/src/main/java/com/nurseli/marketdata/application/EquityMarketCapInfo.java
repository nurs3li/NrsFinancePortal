package com.nurseli.marketdata.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EquityMarketCapInfo(
        BigDecimal marketCapUsd,
        String marketCapSource,
        LocalDateTime marketCapAsOf
) {}
