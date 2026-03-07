package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.equity")
public class EquityProperties {
    /** FinHub hisse sembolleri (örn. GARAN.IS, AKBNK.IS) */
    private List<String> symbols = new ArrayList<>();
}