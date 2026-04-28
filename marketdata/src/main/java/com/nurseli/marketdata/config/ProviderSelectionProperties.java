package com.nurseli.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.providers")
public class ProviderSelectionProperties {

    private final Domain fx = new Domain();
    private final Domain fund = new Domain();
    private final Domain equity = new Domain();

    public Domain getFx() {
        return fx;
    }

    public Domain getFund() {
        return fund;
    }

    public Domain getEquity() {
        return equity;
    }

    public static class Domain {
        private String canonical;
        private List<String> fallbackOrder = new ArrayList<>();

        public String getCanonical() {
            return canonical;
        }

        public void setCanonical(String canonical) {
            this.canonical = canonical;
        }

        public List<String> getFallbackOrder() {
            return fallbackOrder;
        }

        public void setFallbackOrder(List<String> fallbackOrder) {
            this.fallbackOrder = fallbackOrder;
        }
    }
}
