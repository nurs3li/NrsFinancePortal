package com.nurseli.metricsservice.event;



import java.math.BigDecimal;
import java.time.Instant;

public record TradeCreatedEvent(
        Long tradeId,
        Long userId,
        String tradeType,      // "BUY" | "SELL"
        String assetType,      // "FX" | "CRYPTO" | "STOCK" | "METAL" | "FUND"
        String symbol,
        BigDecimal quantity,
        BigDecimal pricePerUnit,
        BigDecimal totalTry,
        Instant occurredAt
) {}