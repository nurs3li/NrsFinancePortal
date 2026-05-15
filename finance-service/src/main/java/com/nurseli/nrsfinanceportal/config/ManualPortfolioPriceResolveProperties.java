package com.nurseli.nrsfinanceportal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.manual-portfolio.price-resolve")
public class ManualPortfolioPriceResolveProperties {

    /**
     * Takvim günü: exact tarih yoksa bu kadar geriye kadar önceki kapanış aranır.
     */
    private int maxLookbackDays = 7;
}
