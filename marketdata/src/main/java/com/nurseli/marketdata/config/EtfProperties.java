package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.etf")
public class EtfProperties {
    /** FinHub free plan: US ETF sembolleri (SPY, QQQ, VOO, ...) */
    private List<String> symbols = new ArrayList<>();
}