package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.market.metals.isyatirim")
@Data
public class MarketMetalsIsyatirimProperties {

    private boolean enabled = true;
    private String baseUrl = "https://www.isyatirim.com.tr";
    private String historicalPath = "/_Layouts/15/IsYatirim.Website/Common/ChartData.aspx/IndexHistoricalAll";
    private int periodMinutes = 1440;
    private int defaultLookbackYears = 2;
    private int latestLookbackDays = 10;
    private int latestFallbackLookbackDays = 30;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 10000;
    private String referer = "https://www.isyatirim.com.tr";
    private String userAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";
    private String sourceLabel = "İş Yatırım";
    private String delayLabel = "Gecikmeli veri";
    private LatestRefresh latestRefresh = new LatestRefresh();

    @Data
    public static class LatestRefresh {
        private boolean enabled = true;
        private long fixedDelayMs = 3_600_000L;
    }
}
