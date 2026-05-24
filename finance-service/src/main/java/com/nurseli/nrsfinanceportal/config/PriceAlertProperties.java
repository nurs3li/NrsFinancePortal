package com.nurseli.nrsfinanceportal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Fiyat alarmı iş kuralları özellikleri.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.price-alerts")
public class PriceAlertProperties {

    private boolean enabled = true;

    /** Varsayılan: her 2 dakika */
    private String evaluationCron = "0 */2 * * * *";

    private int maxActivePerUser = 30;
}
