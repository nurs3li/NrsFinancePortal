package com.nurseli.nrsfinanceportal.integration.kafka.event;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.trade.TradeType;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeCreatedEvent(
        Long tradeId,
        Long userId,
        TradeType tradeType,
        AssetType assetType,
        String symbol,
        BigDecimal quantity,
        BigDecimal pricePerUnit,  // TRY
        BigDecimal totalTry,
        Instant occurredAt
) {}
