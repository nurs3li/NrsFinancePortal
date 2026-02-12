package com.nurseli.nrsfinanceportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Data
@Component
@ConfigurationProperties(prefix = "app.suspicious")
public class SuspiciousProperties {

    private boolean enabled = true;
    /** Dakikada bu sayıdan fazla işlem = şüpheli */
    private int maxTransactionsPerMinute = 20;
    /** Bu tutarı aşan tek işlem = şüpheli */
    private BigDecimal highAmountThreshold = new BigDecimal("500000");
}