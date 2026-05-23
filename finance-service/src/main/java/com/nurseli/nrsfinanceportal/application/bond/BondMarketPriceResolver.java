package com.nurseli.nrsfinanceportal.application.bond;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * finance-service bond piyasa fiyat çözümleyici arayüzü — tahvil sembolü için opsiyonel piyasa fiyatı sağlar.
 */

public interface BondMarketPriceResolver {

    Optional<BigDecimal> resolvePrice(String symbol);
}
