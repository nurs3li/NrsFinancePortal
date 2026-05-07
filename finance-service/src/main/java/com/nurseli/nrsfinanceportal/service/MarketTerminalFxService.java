package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.dto.UsdTryRateResponse;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class MarketTerminalFxService {

    private final MarketDataClient marketDataClient;

    @Transactional(readOnly = true)
    public UsdTryRateResponse usdTryRate() {
        BigDecimal r = marketDataClient.getPriceTry(AssetType.FX, "USDTRY");
        if (r == null || r.signum() <= 0) {
            return new UsdTryRateResponse(BigDecimal.ZERO, false);
        }
        return new UsdTryRateResponse(r, true);
    }
}
