package com.nurseli.nrsfinanceportal.domain.pricing;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;

import java.math.BigDecimal;

/**
 * Sembol fiyat çözümleme domain port arayüzü.
 */
public interface PriceLookupService {

    BigDecimal getTryPrice(AssetType type, String symbol);
}
