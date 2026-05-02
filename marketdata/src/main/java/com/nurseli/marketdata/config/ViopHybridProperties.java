package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.viop.hybrid")
@Data
public class ViopHybridProperties {
    private boolean enabled = false;
    private boolean bistBulletinEnabled = false;
    private boolean priceFallbackEnabled = false;
    private String bistBulletinUrl;
    private String priceFallbackUrl;
    private String priceFallbackApiKey;
    private int timeoutMs = 3000;
    private double suspiciousBasisPctLimit = 15.0;
    private boolean redisEnabled = false;
    private long redisTtlSeconds = 1;
    private boolean kafkaEnabled = false;
    private String kafkaBootstrapServers;
    private String kafkaTopic = "high-volatility-alert";
    private double openInterestSurgeThresholdPct = 10.0;
}

