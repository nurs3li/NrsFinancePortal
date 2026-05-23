package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.api.dto.UsdTryRateResponse;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * finance-service terminal FX servisi — USD/TRY spot kurunu market-data'dan okur.
 */
@RequiredArgsConstructor
@Service

public class MarketTerminalFxService {

    private final MarketDataClient marketDataClient;

    /**
     * {@code usdTryRate} — Güncel USDTRY kurunu döner; bulunamazsa sıfır ve available=false ile yanıtlar.
     */
    @Transactional(readOnly = true)
    public UsdTryRateResponse usdTryRate() {
        BigDecimal r = marketDataClient.getPriceTry(AssetType.FX, "USDTRY");
        if (r == null || r.signum() <= 0) {
            return new UsdTryRateResponse(BigDecimal.ZERO, false);
        }
        return new UsdTryRateResponse(r, true);
    }
}
