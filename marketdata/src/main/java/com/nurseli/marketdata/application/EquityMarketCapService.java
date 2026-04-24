package com.nurseli.marketdata.application;

import com.nurseli.marketdata.infrastructure.finhub.FinHubClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquityMarketCapService {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

    private final FinHubClient finHubClient;

    /**
     * Finnhub profile2 marketCapitalization değeri milyon USD döner.
     * Bu method tam USD değerine normalize edip cache'e yazar.
     */
    @Cacheable(cacheNames = "market:equity-cap", key = "#symbol")
    public EquityMarketCapInfo getMarketCap(String symbol) {
        try {
            var profile = finHubClient.fetchCompanyProfile(symbol).block();
            if (profile == null || profile.getMarketCapitalization() == null || profile.getMarketCapitalization() <= 0) {
                return null;
            }
            return new EquityMarketCapInfo(
                    BigDecimal.valueOf(profile.getMarketCapitalization()).multiply(ONE_MILLION),
                    "FINHUB_PROFILE2",
                    LocalDateTime.now()
            );
        } catch (Exception ex) {
            log.warn("[EQUITY] market cap fetch failed for {}: {}", symbol, ex.getMessage());
            return null;
        }
    }
}
