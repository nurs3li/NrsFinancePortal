package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "market.evds")
@Data
public class EvdsProperties {
    private boolean enabled = true;
    private String apiKey;
    private int timeoutMs = 3000;
    private String baseUrl = "https://evds3.tcmb.gov.tr/igmevdsms-dis";
    /**
     * Mantıksal gösterge → EVDS seri kodu (örn. CPI_TR_INDEX → TP_GENENDEKS_T1).
     * Anahtarlar {@link EvdsSeriesLogicalNames} ile hizalıdır; değerler YAML/env üzerinden gelir.
     * Birincil anahtar boşsa {@code MARKET_MACRO_<LOGICAL_KEY>_CODE} (örn. MARKET_MACRO_DEPOSIT_RATE_USD_1M_WEEKLY_CODE) yedek olarak okunur.
     */
    private Map<String, String> series = new LinkedHashMap<>();
    private Debt debt = new Debt();

    /**
     * @return boş veya yapılandırılmamışsa {@code null}
     */
    public String getSeriesCode(String logicalKey) {
        if (logicalKey == null || series == null) {
            return null;
        }
        String raw = series.get(logicalKey);
        if (raw == null || raw.isBlank()) {
            raw = series.get("MARKET_MACRO_" + logicalKey + "_CODE");
        }
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Data
    public static class Debt {
        private boolean enabled = false;
        private int lookbackDays = 14;
        private java.util.List<Instrument> instruments = new java.util.ArrayList<>();
    }

    @Data
    public static class Instrument {
        private String isin;
        private String name;
        private String issuer;
        private String maturityDate;
        private String dirtyPriceSeries;
        private String yieldSeries;
        private java.math.BigDecimal dirtyPriceScale = java.math.BigDecimal.ONE;
        private java.math.BigDecimal yieldScale = java.math.BigDecimal.ONE;
    }
}
