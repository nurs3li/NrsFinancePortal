package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.trade.TradeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record TradeHistoryDto(

        Long tradeId,
        TradeType tradeType,
        AssetType assetType,
        String symbol,
        BigDecimal quantity,
        BigDecimal totalTry,
        BigDecimal balanceAfter,
        Instant tradedAt
) {

    public BigDecimal tryPrice() {
        if (quantity == null || quantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalTry.divide(quantity, 8, RoundingMode.HALF_UP);
    }
}
