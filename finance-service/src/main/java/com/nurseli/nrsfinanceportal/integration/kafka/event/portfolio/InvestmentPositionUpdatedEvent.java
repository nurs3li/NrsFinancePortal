package com.nurseli.nrsfinanceportal.integration.kafka.event.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InvestmentPositionUpdatedEvent(
        String eventId,
        Instant occurredAt,
        Long userId,
        Long positionId,
        String assetType,
        String symbol,
        BigDecimal quantity,
        LocalDate buyDate,
        BigDecimal buyPrice,
        String buyCurrency,
        BigDecimal currentPrice,
        BigDecimal currentValueTry,
        BigDecimal investedAmountTry,
        BigDecimal nominalProfitTry,
        BigDecimal realProfitTry,
        String status
) {}
