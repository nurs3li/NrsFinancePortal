package com.nurseli.whaleanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "whale.thresholds")
public class WhaleThresholdProperties {

    private Level l1;
    private Level l2;
    private Level l3;

    public static class Level {
        private BigDecimal singleTx;
        private BigDecimal dailyVolume;
        private int hourlyCount;

        public BigDecimal getSingleTx() {
            return singleTx;
        }

        public void setSingleTx(BigDecimal singleTx) {
            this.singleTx = singleTx;
        }

        public BigDecimal getDailyVolume() {
            return dailyVolume;
        }

        public void setDailyVolume(BigDecimal dailyVolume) {
            this.dailyVolume = dailyVolume;
        }

        public int getHourlyCount() {
            return hourlyCount;
        }

        public void setHourlyCount(int hourlyCount) {
            this.hourlyCount = hourlyCount;
        }
    }

    public Level getL1() {
        return l1;
    }

    public void setL1(Level l1) {
        this.l1 = l1;
    }

    public Level getL2() {
        return l2;
    }

    public void setL2(Level l2) {
        this.l2 = l2;
    }

    public Level getL3() {
        return l3;
    }

    public void setL3(Level l3) {
        this.l3 = l3;
    }
}
