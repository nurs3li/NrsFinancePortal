package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Grafik/liste okunurken DB kuyruğu eskiyse dış kaynaktan eksik günleri tamamlama.
 */
@Configuration
@ConfigurationProperties(prefix = "market.stale-tail")
@Data
public class MarketStaleTailProperties {

    private boolean enabled = true;

    /** Son kayıt bu kadar günden eskiyse onarım tetiklenir (hafta sonu için ≥2 önerilir). */
    private int days = 3;
}
