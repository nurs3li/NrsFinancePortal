package com.nurseli.marketdata.infrastructure.tefas;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "tefas")
public class TefasProperties {

    private List<String> funds;

    public List<String> getFunds() {
        return funds;
    }

    public void setFunds(List<String> funds) {
        this.funds = funds;
    }
}