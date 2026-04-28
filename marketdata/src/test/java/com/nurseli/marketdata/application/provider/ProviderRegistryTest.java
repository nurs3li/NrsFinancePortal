package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.config.ProviderSelectionProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderRegistryTest {

    @Test
    void shouldPickConfiguredCanonicalAndFallbackOrder() {
        ProviderSelectionProperties props = new ProviderSelectionProperties();
        props.getFx().setCanonical("TCMB");
        props.getFx().setFallbackOrder(List.of("BANK", "EVDS"));
        props.getFund().setCanonical("ETF");
        props.getFund().setFallbackOrder(List.of());
        props.getEquity().setCanonical("FINHUB");

        FxProvider tcmb = new StubFx("TCMB", true);
        FxProvider bank = new StubFx("BANK", false);
        FxProvider evds = new StubFx("EVDS", false);
        FundProvider etf = new StubFund("ETF", true);
        EquityProvider finhub = new StubEq("FINHUB", true);

        ProviderRegistry registry = new ProviderRegistry(
                List.of(tcmb, bank, evds),
                List.of(etf),
                List.of(finhub),
                props
        );

        assertEquals("TCMB", registry.fxCanonical().providerName());
        assertEquals(List.of("BANK", "EVDS"), registry.fxFallbackOrder().stream().map(FxProvider::providerName).toList());
        assertEquals("ETF", registry.fundCanonical().providerName());
        assertEquals(List.of(), registry.fundFallbackOrder().stream().map(FundProvider::providerName).toList());
    }

    private record StubFx(String providerName, boolean isCanonical) implements FxProvider {
        @Override public java.util.Map<String, com.nurseli.marketdata.api.dto.MarketPriceLatestResponse> getLatest() { return java.util.Map.of(); }
        @Override public java.util.List<com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse> getHistory(String symbol, int days) { return java.util.List.of(); }
    }
    private record StubFund(String providerName, boolean isCanonical) implements FundProvider {
        @Override public java.util.Map<String, com.nurseli.marketdata.api.dto.MarketPriceLatestResponse> getLatest() { return java.util.Map.of(); }
        @Override public java.util.List<com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse> getHistory(String symbol, int days) { return java.util.List.of(); }
    }
    private record StubEq(String providerName, boolean isCanonical) implements EquityProvider {
        @Override public java.util.Map<String, com.nurseli.marketdata.api.dto.MarketPriceLatestResponse> getLatest() { return java.util.Map.of(); }
        @Override public java.util.List<com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse> getHistory(String symbol, int days) { return java.util.List.of(); }
    }
}
