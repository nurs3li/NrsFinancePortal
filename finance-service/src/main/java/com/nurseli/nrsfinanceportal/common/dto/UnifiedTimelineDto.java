package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.trade.TradeType;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleLevel;

import java.math.BigDecimal;
import java.time.Instant;

public record UnifiedTimelineDto(

        TimelineType type,      // TRADE | WHALE
        Instant occurredAt,

        // ---- TRADE ----
        Long tradeId,
        TradeType tradeType,
        AssetType assetType,
        String symbol,
        BigDecimal quantity,
        BigDecimal totalTry,
        BigDecimal balanceAfter,

        // ---- WHALE ----
        WhaleLevel whaleLevel,
        BigDecimal whaleImpactScore
) {
}
