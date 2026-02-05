package com.nurseli.nrsfinanceportal.service.trade;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.trade.TradeType;

import java.math.BigDecimal;

/**
 * UI'dan gelen trade isteği.
 */
public record TradeRequest(
        AssetType assetType,   // CRYPTO, FX, FUND, METAL
        String symbol,          // BTCUSDT, USDTRY, AES...
        BigDecimal quantity,
        TradeType tradeType     // BUY / SELL
) {}
