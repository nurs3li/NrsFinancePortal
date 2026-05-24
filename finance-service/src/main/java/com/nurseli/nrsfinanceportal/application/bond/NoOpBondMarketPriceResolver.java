package com.nurseli.nrsfinanceportal.application.bond;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * finance-service bond fiyat stub'Ä± â€” piyasa fiyatÄ± Ã§Ã¶zÃ¼mlemesi yapmayan varsayÄ±lan BondMarketPriceResolver implementasyonu.
 */
@Component

public class NoOpBondMarketPriceResolver implements BondMarketPriceResolver {

    /**
     * {@code resolvePrice} â€” Her zaman boÅŸ Optional dÃ¶ner; fiyat pozisyon entity'sinden okunur.
     */
    @Override
    public Optional<BigDecimal> resolvePrice(String symbol) {
        return Optional.empty();
    }
}
