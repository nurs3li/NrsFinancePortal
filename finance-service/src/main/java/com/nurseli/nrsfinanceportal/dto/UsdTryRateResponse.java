package com.nurseli.nrsfinanceportal.dto;

import java.math.BigDecimal;

/**
 * Piyasa terminali: USD ile kotasyonlu satırları TL’ye çevirmek için güncel USDTRY (TRY/USD) kuru.
 */
public record UsdTryRateResponse(
        BigDecimal rate,
        boolean available
) {}
