package com.nurseli.nrsfinanceportal.service.trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trade sonucu UI'ya dönen cevap.
 */
public record TradeResponse(
        String symbol,
        BigDecimal quantity,
        BigDecimal tryPrice,
        BigDecimal totalTry,
        BigDecimal balanceAfter,
        LocalDateTime timestamp
) {}