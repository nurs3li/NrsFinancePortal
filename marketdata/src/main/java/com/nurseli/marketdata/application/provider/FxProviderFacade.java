package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class FxProviderFacade {

    private final ProviderRegistry providerRegistry;
    private final MarketPriceQueryService queryService;
    private final MeterRegistry meterRegistry;

    public FxProviderFacade(
            ProviderRegistry providerRegistry,
            MarketPriceQueryService queryService,
            MeterRegistry meterRegistry
    ) {
        this.providerRegistry = providerRegistry;
        this.queryService = queryService;
        this.meterRegistry = meterRegistry;
    }

    public MarketPriceLatestResponse getLatest(String symbol) {
        FxProvider primary = providerRegistry.fxCanonical();
        MarketPriceLatestResponse fromPrimary = tryProvider(primary, symbol, PriceQuality.EXACT);
        if (fromPrimary != null) {
            return fromPrimary;
        }
        for (FxProvider fallback : providerRegistry.fxFallbackOrder()) {
            MarketPriceLatestResponse fromFallback = tryProvider(fallback, symbol, PriceQuality.FALLBACK);
            if (fromFallback != null) {
                meterRegistry.counter("fx_provider_fallback_total", "from", primary.getName(), "to", fallback.getName()).increment();
                return fromFallback;
            }
        }
        try {
            MarketPriceLatestResponse stale = queryService.getLatestOrThrow(symbol);
            meterRegistry.counter("fx_provider_degraded_total", "stage", "DB_LAST").increment();
            return withQuality(stale, stale.source() == null ? "DB_LAST_KNOWN" : stale.source(), PriceQuality.STALE);
        } catch (Exception ex) {
            meterRegistry.counter("fx_provider_failure_total", "provider", "DB_LAST").increment();
            return null;
        }
    }

    private MarketPriceLatestResponse tryProvider(FxProvider provider, String symbol, PriceQuality quality) {
        try {
            MarketPriceLatestResponse result = provider.getLatest(symbol);
            if (result == null) {
                meterRegistry.counter("fx_provider_failure_total", "provider", provider.getName()).increment();
                return null;
            }
            meterRegistry.counter("fx_provider_success_total", "provider", provider.getName()).increment();
            String resolvedSource = (result.source() == null || result.source().isBlank()) ? provider.getName() : result.source();
            return withQuality(result, resolvedSource, quality);
        } catch (Exception ex) {
            meterRegistry.counter("fx_provider_failure_total", "provider", provider.getName()).increment();
            return null;
        }
    }

    private MarketPriceLatestResponse withQuality(
            MarketPriceLatestResponse row,
            String source,
            PriceQuality quality
    ) {
        return new MarketPriceLatestResponse(
                row.symbol(),
                row.buyPrice(),
                row.sellPrice(),
                source,
                row.timestamp(),
                row.asOf() != null ? row.asOf() : row.timestamp(),
                quality,
                row.marketCap(),
                row.marketCapSource(),
                row.marketCapAsOf()
        );
    }
}
