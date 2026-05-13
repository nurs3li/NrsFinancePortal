package com.nurseli.whaleanalytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Data
@ConfigurationProperties(prefix = "app.investor-behavior")
public class InvestorBehaviorAnalysisProperties {

    private Thresholds thresholds = new Thresholds();

    @Data
    public static class Thresholds {
        private BigDecimal l1PortfolioTry = new BigDecimal("250000");
        private BigDecimal l2PortfolioTry = new BigDecimal("1000000");
        private BigDecimal l3PortfolioTry = new BigDecimal("5000000");
        private BigDecimal l1LargestPositionTry = new BigDecimal("100000");
        private BigDecimal l3LargestPositionTry = new BigDecimal("2000000");
        private BigDecimal l2ConcentrationRatio = new BigDecimal("0.40");
        private int l1Score = 35;
        private int l2Score = 65;
        private int l3Score = 85;
    }
}
