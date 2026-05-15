package com.nurseli.marketdata.application.deposit;

import com.nurseli.marketdata.config.DepositRatesProperties;
import com.nurseli.marketdata.config.EvdsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mevduat serileri: önce {@code market.deposit-rates.series} (manuel override); boşsa
 * {@link DepositRateCatalog} + {@code market.evds.series} (kredi faizleriyle aynı EVDS env ailesi).
 */
@Component
@RequiredArgsConstructor
public class DepositRatesSeriesResolver {

    private final DepositRatesProperties depositRatesProperties;
    private final EvdsProperties evdsProperties;

    /**
     * @param logicalIndicatorCode EVDS mantıksal anahtar (örn. DEPOSIT_RATE_TRY_1M_WEEKLY); YAML override satırında
     *                             {@link DepositRatesProperties.SeriesEntry#getLogicalIndicatorCode()} dolu olabilir.
     * @param seriesCode           EVDS seri kodu (örn. TP_TRY_MT01)
     */
    public record ResolvedDepositSeries(String logicalIndicatorCode, String seriesCode, String currency, String term) {}

    public List<ResolvedDepositSeries> resolved() {
        List<DepositRatesProperties.SeriesEntry> yaml = depositRatesProperties.getSeries();
        if (yaml != null && !yaml.isEmpty()) {
            List<ResolvedDepositSeries> out = new ArrayList<>();
            for (DepositRatesProperties.SeriesEntry s : yaml) {
                if (s == null) {
                    continue;
                }
                String code = s.getSeriesCode() == null ? "" : s.getSeriesCode().trim();
                if (code.isEmpty()) {
                    continue;
                }
                String logical = s.getLogicalIndicatorCode() == null ? null : s.getLogicalIndicatorCode().trim();
                if (logical != null && logical.isEmpty()) {
                    logical = null;
                }
                String ccy = s.getCurrency() == null ? "" : s.getCurrency().trim().toUpperCase();
                String term = s.getTerm() == null ? "" : s.getTerm().trim();
                out.add(new ResolvedDepositSeries(logical, code, ccy, term));
            }
            return List.copyOf(out);
        }
        return DepositRateCatalog.allSpecs().stream()
                .map(sp -> {
                    String code = evdsProperties.getSeriesCode(sp.evdsLogicalKey());
                    return new ResolvedDepositSeries(sp.evdsLogicalKey(), code, sp.currency(), sp.term());
                })
                .filter(r -> r.seriesCode() != null && !r.seriesCode().isBlank())
                .toList();
    }
}
