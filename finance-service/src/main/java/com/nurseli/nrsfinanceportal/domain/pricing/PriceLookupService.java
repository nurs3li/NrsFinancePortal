package com.nurseli.nrsfinanceportal.domain.pricing;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;

public interface PriceLookupService {

    BigDecimal getTryPrice(AssetType type, String symbol);
}
