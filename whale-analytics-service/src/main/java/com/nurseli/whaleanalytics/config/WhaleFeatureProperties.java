package com.nurseli.whaleanalytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.whale")
public class WhaleFeatureProperties {

    /**
     * Transaction tabanlı whale tüketimi (finance.transaction.created).
     */
    private boolean legacyTransactionAnalysisEnabled = false;

    /**
     * Manuel yatırım pozisyonlarından investor behavior analizi.
     */
    private boolean portfolioBehaviorAnalysisEnabled = true;
}
