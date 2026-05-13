package com.nurseli.whaleanalytics.event.investment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvestmentPositionClosedEvent(
        String eventId,
        Instant occurredAt,
        Long userId,
        Long positionId,
        String assetType,
        String symbol,
        BigDecimal quantity,
        LocalDate sellDate,
        BigDecimal sellPrice,
        String sellCurrency,
        Instant sellResolvedDate,
        String sellPriceSource,
        BigDecimal investedAmountTry,
        BigDecimal sellValueTry,
        BigDecimal realizedProfitTry,
        BigDecimal realProfitTry,
        String status
) {}
