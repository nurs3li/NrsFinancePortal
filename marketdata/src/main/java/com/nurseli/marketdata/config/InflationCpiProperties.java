package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market.inflation.cpi")
@Data
public class InflationCpiProperties {
    private boolean persistEnabled = true;
    private int baseYear = 2003;
    private boolean schedulerEnabled = false;
    private String schedulerCron = "0 0 7 3 * *";
}
