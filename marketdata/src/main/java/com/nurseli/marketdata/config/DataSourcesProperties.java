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
        /**
         * Ücretsiz: https://api.coingecko.com/api/v3 — Pro: https://pro-api.coingecko.com/api/v3
         */
        private String url;
        /**
         * CoinGecko Pro/Demo anahtarı. Boşsa ücretsiz kotaya düşer; OHLC yoğun backfill'de 429 sık görülür.
         */
        private String apiKey = "";
        private int connectTimeoutMs = 3000;
        /** OHLC gibi büyük yanıtlar için okuma süresi */
        private int readTimeoutMs = 15000;
    }

    @Data
    public static class FinHub {
        private String url;
        private String apiKey;
        private boolean candleEnabled = true;
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