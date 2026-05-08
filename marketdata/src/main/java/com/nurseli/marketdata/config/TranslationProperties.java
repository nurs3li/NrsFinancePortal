package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.translation")
@Data
public class TranslationProperties {
    private boolean enabled = true;
    private String provider = "googlefree";
    private String baseUrl = "http://localhost:5000";
    private int timeoutMs = 2500;
}

