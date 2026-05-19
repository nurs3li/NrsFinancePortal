package com.nurseli.marketdata.application.debt;

import com.nurseli.marketdata.config.EvdsProperties;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EvdsDebtSeriesLookup {

    private final Map<String, EvdsProperties.Instrument> byIsin;

    public EvdsDebtSeriesLookup(EvdsProperties evdsProperties) {
        var debt = evdsProperties.getDebt();
        if (debt == null || debt.getInstruments() == null) {
            this.byIsin = Map.of();
        } else {
            this.byIsin = debt.getInstruments().stream()
                    .filter(i -> i != null && i.getIsin() != null && !i.getIsin().isBlank())
                    .collect(Collectors.toMap(
                            i -> i.getIsin().trim().toUpperCase(Locale.ROOT),
                            i -> i,
                            (a, b) -> a));
        }
    }

    /** Kirli fiyat veya kupon seri kodundan çıkarım için referans (TP_…). */
    public String referenceSeriesCode(String isin) {
        if (isin == null || isin.isBlank()) {
            return null;
        }
        EvdsProperties.Instrument ins = byIsin.get(isin.trim().toUpperCase(Locale.ROOT));
        if (ins == null) {
            return null;
        }
        String price = ins.getDirtyPriceSeries();
        String coupon = ins.couponRateSeries();
        if (price != null && !price.isBlank() && coupon != null && !coupon.isBlank()) {
            return price.trim() + "|" + coupon.trim();
        }
        if (price != null && !price.isBlank()) {
            return price.trim();
        }
        return coupon != null && !coupon.isBlank() ? coupon.trim() : null;
    }
}
