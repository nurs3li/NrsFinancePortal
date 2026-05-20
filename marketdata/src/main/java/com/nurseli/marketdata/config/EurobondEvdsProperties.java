package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.market.eurobonds.evds")
@Data
public class EurobondEvdsProperties {

    private boolean enabled = true;

    private int defaultLookbackYears = 5;

    private LatestRefresh latestRefresh = new LatestRefresh();

    private Series series = new Series();

    @Data
    public static class LatestRefresh {
        private boolean enabled = true;
        private long fixedDelayMs = 86_400_000L;
    }

    @Data
    public static class Series {
        private String bookValue = "TP_EBONDYAZDEG_ST";
        private String marketValue = "TP_EBONDPIYDEG_ST";
        private String total = "TP_EBONDVADE_C8_ST";
        private String originalMaturityShort = "TP_EBONDVADE_C1_ST";
        private String originalMaturityLong = "TP_EBONDVADE_C2_ST";
        private String remainingMaturityShort = "TP_EBONDVADE_C3_ST";
        private String remainingMaturityLong = "TP_EBONDVADE_C4_ST";
        private String currencyUsd = "TP_EBONDVADE_C5_ST";
        private String currencyEur = "TP_EBONDVADE_C6_ST";
        private String currencyJpy = "TP_EBONDVADE_C7_ST";
    }
}
