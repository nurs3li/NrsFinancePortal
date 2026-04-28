package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.config.ProviderSelectionProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class ProviderRegistry {

    private final List<FxProvider> fxProviders;
    private final List<FundProvider> fundProviders;
    private final List<EquityProvider> equityProviders;
    private final ProviderSelectionProperties providerSelectionProperties;

    public ProviderRegistry(
            List<FxProvider> fxProviders,
            List<FundProvider> fundProviders,
            List<EquityProvider> equityProviders,
            ProviderSelectionProperties providerSelectionProperties
    ) {
        this.fxProviders = fxProviders;
        this.fundProviders = fundProviders;
        this.equityProviders = equityProviders;
        this.providerSelectionProperties = providerSelectionProperties;
    }

    public FxProvider fxCanonical() {
        String configured = providerSelectionProperties.getFx().getCanonical();
        if (configured != null && !configured.isBlank()) {
            return fxProviders.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(configured))
                    .findFirst()
                    .orElseGet(() -> fxProviders.stream().findFirst().orElseThrow());
        }
        return fxProviders.stream().filter(FxProvider::isCanonical).findFirst()
                .orElseGet(() -> fxProviders.stream().findFirst().orElseThrow());
    }

    public FxProvider fxByNameOrCanonical(String provider) {
        if (provider == null || provider.isBlank() || "canonical".equalsIgnoreCase(provider)) {
            return fxCanonical();
        }
        return fxProviders.stream()
                .filter(p -> p.providerName().equalsIgnoreCase(provider))
                .findFirst()
                .orElse(fxCanonical());
    }

    public FundProvider fundCanonical() {
        String configured = providerSelectionProperties.getFund().getCanonical();
        if (configured != null && !configured.isBlank()) {
            return fundProviders.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(configured))
                    .findFirst()
                    .orElseGet(() -> fundProviders.stream().findFirst().orElseThrow());
        }
        return fundProviders.stream().filter(FundProvider::isCanonical).findFirst()
                .orElseGet(() -> fundProviders.stream().findFirst().orElseThrow());
    }

    public EquityProvider equityCanonical() {
        String configured = providerSelectionProperties.getEquity().getCanonical();
        if (configured != null && !configured.isBlank()) {
            return equityProviders.stream()
                    .filter(p -> p.providerName().equalsIgnoreCase(configured))
                    .findFirst()
                    .orElseGet(() -> equityProviders.stream().findFirst().orElseThrow());
        }
        return equityProviders.stream().filter(EquityProvider::isCanonical).findFirst()
                .orElseGet(() -> equityProviders.stream().findFirst().orElseThrow());
    }

    public List<FxProvider> fxFallbackOrder() {
        List<String> names = providerSelectionProperties.getFx().getFallbackOrder();
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .map(name -> fxProviders.stream().filter(p -> p.providerName().equalsIgnoreCase(name)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<FundProvider> fundFallbackOrder() {
        List<String> names = providerSelectionProperties.getFund().getFallbackOrder();
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .map(name -> fundProviders.stream().filter(p -> p.providerName().equalsIgnoreCase(name)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }
}
