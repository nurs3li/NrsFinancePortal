package com.nurseli.marketdata.application.fx;

import com.nurseli.marketdata.api.dto.fx.FxEffectiveRateRowDto;
import com.nurseli.marketdata.api.dto.fx.FxEffectiveRatesResponseDto;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * TCMB/EVDS günlük döviz alış/satış ile efektif (nakit) alış/satış serilerini birleştirir.
 * Mevcut {@code /api/market/doviz} sağlayıcıları ve portföy değerleme akışına dokunmaz.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FxEffectiveRatesService {

    private static final String SOURCE = "EVDS";
    private static final String FREQUENCY = "DAILY";

    /** EVDS seri kodları (YAML override yok; sabit TCMB kodları). */
    private static final Map<String, String[]> SERIES_BY_CCY = Map.of(
            "USD", new String[] {"TP_DK_USD_A_YTL", "TP_DK_USD_S_YTL", "TP_DK_USD_A_EF_YTL", "TP_DK_USD_S_EF_YTL"},
            "EUR", new String[] {"TP_DK_EUR_A_YTL", "TP_DK_EUR_S_YTL", "TP_DK_EUR_A_EF_YTL", "TP_DK_EUR_S_EF_YTL"},
            "GBP", new String[] {"TP_DK_GBP_A_YTL", "TP_DK_GBP_S_YTL", "TP_DK_GBP_A_EF_YTL", "TP_DK_GBP_S_EF_YTL"});

    private final EvdsDebtClient evdsDebtClient;
    private final EvdsProperties evdsProperties;

    @Value("${market.fx.effective-rates.lookback-days:400}")
    private int lookbackDays;

    public FxEffectiveRatesResponseDto load() {
        if (!evdsProperties.isEnabled()) {
            return new FxEffectiveRatesResponseDto(
                    Instant.now().toString(),
                    SOURCE,
                    FREQUENCY,
                    List.of(),
                    List.of("EVDS devre dışı; kurlar boş döndü."));
        }
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(Math.max(30, lookbackDays));
        List<FxEffectiveRateRowDto> all = new ArrayList<>();
        for (String ccy : List.of("USD", "EUR", "GBP")) {
            List<FxEffectiveRateRowDto> part = buildForCurrency(ccy, start, end);
            all.addAll(part);
            logLatest(ccy, part);
        }
        all.sort(Comparator.comparing(FxEffectiveRateRowDto::date).reversed().thenComparing(FxEffectiveRateRowDto::currency));
        List<String> notes = List.of(
                "Kaynak: TCMB/EVDS günlük gösterge kurlarıdır; banka veya işlem masası kuru değildir.",
                "Efektif alış/satış nakit döviz işlemleri için gösterge niteliğindedir; getiri/yield değildir.");
        return new FxEffectiveRatesResponseDto(Instant.now().toString(), SOURCE, FREQUENCY, all, notes);
    }

    private List<FxEffectiveRateRowDto> buildForCurrency(String currency, LocalDate start, LocalDate end) {
        String[] codes = SERIES_BY_CCY.get(currency);
        if (codes == null || codes.length != 4) {
            return List.of();
        }
        Map<LocalDate, BigDecimal> fxBuy = fetchSeriesMap(codes[0], start, end);
        Map<LocalDate, BigDecimal> fxSell = fetchSeriesMap(codes[1], start, end);
        Map<LocalDate, BigDecimal> cashBuy = fetchSeriesMap(codes[2], start, end);
        Map<LocalDate, BigDecimal> cashSell = fetchSeriesMap(codes[3], start, end);

        Set<LocalDate> dates = new TreeSet<>();
        dates.addAll(fxBuy.keySet());
        dates.addAll(fxSell.keySet());
        dates.addAll(cashBuy.keySet());
        dates.addAll(cashSell.keySet());

        LinkedHashSet<LocalDate> desc = new LinkedHashSet<>();
        dates.stream().sorted(Comparator.reverseOrder()).forEach(desc::add);

        List<FxEffectiveRateRowDto> out = new ArrayList<>();
        for (LocalDate d : desc) {
            BigDecimal fb = fxBuy.get(d);
            BigDecimal fs = fxSell.get(d);
            BigDecimal cb = cashBuy.get(d);
            BigDecimal cs = cashSell.get(d);
            if (fb == null && fs == null && cb == null && cs == null) {
                continue;
            }
            Double fxSpread = diff(fs, fb);
            Double cashSpread = diff(cs, cb);
            Double cashVsFxBuyingDiff = diff(cb, fb);
            Double cashVsFxSellingDiff = diff(cs, fs);
            out.add(new FxEffectiveRateRowDto(
                    currency,
                    d.toString(),
                    toDouble(fb),
                    toDouble(fs),
                    toDouble(cb),
                    toDouble(cs),
                    fxSpread,
                    cashSpread,
                    cashVsFxBuyingDiff,
                    cashVsFxSellingDiff));
        }
        return out;
    }

    private Map<LocalDate, BigDecimal> fetchSeriesMap(String seriesCode, LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> map = new HashMap<>();
        try {
            List<EvdsSeriesPoint> pts = evdsDebtClient.fetchSeriesAscending(seriesCode, start, end);
            for (EvdsSeriesPoint p : pts) {
                if (p == null || p.value() == null) {
                    continue;
                }
                map.put(p.asOf().toLocalDate(), p.value());
            }
        } catch (Exception ex) {
            log.warn("[FX_EFFECTIVE] series fetch failed code={} msg={}", seriesCode, ex.getMessage());
        }
        return map;
    }

    private static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    /** a - b; eksik terimde {@code null}. */
    private static Double diff(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return null;
        }
        return a.subtract(b).doubleValue();
    }

    private void logLatest(String currency, List<FxEffectiveRateRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            log.debug("[FX_EFFECTIVE] currency={} no rows", currency);
            return;
        }
        FxEffectiveRateRowDto r = rows.get(0);
        log.info(
                "[FX_EFFECTIVE] currency={} date={} fxBuying={} fxSelling={} cashBuying={} cashSelling={}",
                currency,
                r.date(),
                r.fxBuying(),
                r.fxSelling(),
                r.cashBuying(),
                r.cashSelling());
    }
}
