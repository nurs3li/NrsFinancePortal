package com.nurseli.nrsfinanceportal.service.bond;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class NoOpBondMarketPriceResolver implements BondMarketPriceResolver {

    @Override
    public Optional<BigDecimal> resolvePrice(String symbol) {
        return Optional.empty();
    }
}
