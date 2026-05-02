package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market.evds")
@Data
public class EvdsProperties {
    private boolean enabled = true;
    private String apiKey;
    private int timeoutMs = 3000;
    private String baseUrl = "https://evds3.tcmb.gov.tr/igmevdsms-dis";
    private Debt debt = new Debt();

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
