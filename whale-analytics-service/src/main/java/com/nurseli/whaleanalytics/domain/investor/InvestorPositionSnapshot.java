package com.nurseli.whaleanalytics.domain.investor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Redis'te saklanan pozisyon özeti (açık veya kapalı).
 */
public record InvestorPositionSnapshot(
        Long positionId,
        Long userId,
        String assetType,
        String symbol,
        BigDecimal quantity,
        String status,
        BigDecimal investedAmountTry,
        BigDecimal currentValueTry,
        BigDecimal closedValueTry,
        BigDecimal nominalProfitTry,
        BigDecimal realProfitTry,
        Instant updatedAt
) {}
