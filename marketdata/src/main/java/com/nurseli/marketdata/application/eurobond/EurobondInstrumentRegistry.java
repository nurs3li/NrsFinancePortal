package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.config.ConditionalOnEurobondEvds;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@ConditionalOnEurobondEvds
public class EurobondInstrumentRegistry {

    private final List<ResolvedInstrument> all;
    private final Map<String, ResolvedInstrument> byIsin;

    public EurobondInstrumentRegistry(EurobondEvdsProperties properties) {
        EurobondEvdsProperties.Instruments cfg = properties.getInstruments();
        List<ResolvedInstrument> list = (cfg.getList() == null ? List.<EurobondEvdsProperties.Instrument>of() : cfg.getList())
                .stream()
                .filter(i -> i != null && i.getIsin() != null && !i.getIsin().isBlank())
                .map(this::resolve)
                .toList();
        this.all = List.copyOf(list);
        this.byIsin = list.stream().collect(Collectors.toUnmodifiableMap(
                i -> i.isin(),
                i -> i,
                (a, b) -> a));
    }

    public List<ResolvedInstrument> all() {
        return all;
    }

    public Optional<ResolvedInstrument> findByIsin(String isin) {
        if (isin == null || isin.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIsin.get(isin.trim().toUpperCase(Locale.ROOT)));
    }

    private ResolvedInstrument resolve(EurobondEvdsProperties.Instrument ins) {
        return new ResolvedInstrument(
                ins.getIsin().trim().toUpperCase(Locale.ROOT),
                ins.getName() != null ? ins.getName().trim() : ins.getIsin(),
                ins.getIssuer() != null ? ins.getIssuer().trim() : "Hazine",
                ins.getCurrency() != null ? ins.getCurrency().trim().toUpperCase(Locale.ROOT) : "USD",
                ins.getCouponPct(),
                ins.getMaturityDate(),
                blankToNull(ins.getDirtyPriceSeries()),
                blankToNull(ins.getYieldSeries()),
                ins.getDirtyPriceScale() != null ? ins.getDirtyPriceScale() : java.math.BigDecimal.ONE,
                ins.getYieldScale() != null ? ins.getYieldScale() : java.math.BigDecimal.ONE,
                ins.getReferenceCleanPrice(),
                ins.getReferenceYieldPct(),
                ins.getMinLotUsd() != null ? ins.getMinLotUsd() : 200_000L);
    }

    private static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }

    public record ResolvedInstrument(
            String isin,
            String name,
            String issuer,
            String currency,
            java.math.BigDecimal couponPct,
            String maturityDate,
            String dirtyPriceSeries,
            String yieldSeries,
            java.math.BigDecimal dirtyPriceScale,
            java.math.BigDecimal yieldScale,
            java.math.BigDecimal referenceCleanPrice,
            java.math.BigDecimal referenceYieldPct,
            Long minLotUsd) {}
}
