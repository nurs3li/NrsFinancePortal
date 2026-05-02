package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.data-sources")
@Data
public class DataSourcesProperties {

    private Tcmb tcmb = new Tcmb();
    private CoinGecko coingecko = new CoinGecko();
    private FinHub finhub = new FinHub();
    private Stooq stooq = new Stooq();
    private Yahoo yahoo = new Yahoo();

    @Data
    public static class Tcmb {
        private String url;
    }

    @Data
    public static class CoinGecko {
        private String url;
    }

    @Data
    public static class FinHub {
        private String url;
        private String apiKey;
    }

    @Data
    public static class Stooq {
        private String url;
        private String apiKey;
    }

    @Data
    public static class Yahoo {
        private String url;
    }
}