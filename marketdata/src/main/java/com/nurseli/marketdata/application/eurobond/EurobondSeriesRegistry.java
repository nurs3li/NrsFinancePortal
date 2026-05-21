package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.config.ConditionalOnEurobondEvds;
import com.nurseli.marketdata.config.EurobondEvdsProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnEurobondEvds
public class EurobondSeriesRegistry {

    public static final String FREQUENCY_LABEL = "Haftalık";
    public static final String UNIT_LABEL = "milyon ABD doları";
    public static final String SOURCE_LABEL = "TCMB EVDS";

    private final List<ResolvedSeries> all;
    private final Map<String, ResolvedSeries> byCode;
    private final Map<String, ResolvedSeries> byKey;

    public EurobondSeriesRegistry(EurobondEvdsProperties properties) {
        EurobondEvdsProperties.Series s = properties.getSeries();
        List<ResolvedSeries> list = new ArrayList<>();
        list.add(entry("book-value", s.getBookValue(), "Genel Yönetim Eurobondları Yazılı Değer"));
        list.add(entry("market-value", s.getMarketValue(), "Genel Yönetim Eurobondları Piyasa Değeri"));
        list.add(entry("total-distribution", s.getTotal(), "Vade ve Para Dağılımı Toplam"));
        list.add(entry("original-maturity-short", s.getOriginalMaturityShort(), "Orijinal Vade - Kısa"));
        list.add(entry("original-maturity-long", s.getOriginalMaturityLong(), "Orijinal Vade - Uzun"));
        list.add(entry("remaining-maturity-short", s.getRemainingMaturityShort(), "Kalan Vade - Kısa"));
        list.add(entry("remaining-maturity-long", s.getRemainingMaturityLong(), "Kalan Vade - Uzun"));
        list.add(entry("currency-usd", s.getCurrencyUsd(), "ABD Doları İhraçlar"));
        list.add(entry("currency-eur", s.getCurrencyEur(), "Euro İhraçlar"));
        list.add(entry("currency-jpy", s.getCurrencyJpy(), "JPY İhraçlar"));
        this.all = List.copyOf(list);
        Map<String, ResolvedSeries> codeMap = new LinkedHashMap<>();
        Map<String, ResolvedSeries> keyMap = new LinkedHashMap<>();
        for (ResolvedSeries rs : list) {
            if (rs.seriesCode() != null && !rs.seriesCode().isBlank()) {
                codeMap.put(rs.seriesCode().trim().toUpperCase(Locale.ROOT), rs);
            }
            keyMap.put(rs.seriesKey(), rs);
        }
        this.byCode = Map.copyOf(codeMap);
        this.byKey = Map.copyOf(keyMap);
    }

    public List<ResolvedSeries> all() {
        return all;
    }

    public Optional<ResolvedSeries> findByCode(String seriesCode) {
        if (seriesCode == null || seriesCode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(seriesCode.trim().toUpperCase(Locale.ROOT)));
    }

    public Optional<ResolvedSeries> findByKey(String seriesKey) {
        if (seriesKey == null || seriesKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(seriesKey.trim()));
    }

    public List<ResolvedSeries> resolveCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return List.of();
        }
        List<ResolvedSeries> out = new ArrayList<>();
        for (String raw : rawCodes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            findByCode(raw).ifPresent(out::add);
        }
        return out;
    }

    private static ResolvedSeries entry(String key, String code, String label) {
        String trimmed = code == null ? "" : code.trim();
        return new ResolvedSeries(key, trimmed, label);
    }

    public record ResolvedSeries(String seriesKey, String seriesCode, String label) {}
}
