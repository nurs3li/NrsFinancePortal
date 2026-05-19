package com.nurseli.nrsfinanceportal.service.bond;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * MVP: bond prices are manual on the position entity.
 * Future: resolve from market-data debt endpoints.
 */
public interface BondMarketPriceResolver {

    Optional<BigDecimal> resolvePrice(String symbol);
}
