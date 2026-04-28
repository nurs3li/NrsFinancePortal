package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.infrastructure.tcmb.TcmbRate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TCMB döviz kurları ingest (scheduler / startup) sonrası bellekte tutulur.
 * HTTP istekleri buradan okur; canlı XML çağrısı yapılmaz.
 */
@Component
public class TcmbFxSnapshotCache {

    private volatile Map<String, MarketPriceLatestResponse> rates = Map.of();
    private volatile Instant updatedAt = Instant.EPOCH;

    public synchronized void replaceFromTcmbRates(List<TcmbRate> tcmbRates, LocalDateTime asOf) {
        if (tcmbRates == null || tcmbRates.isEmpty()) {
            return;
        }
        Map<String, MarketPriceLatestResponse> next = new LinkedHashMap<>();
        for (TcmbRate rate : tcmbRates) {
            String sym = rate.symbol() + "TRY";
            next.put(
                    sym,
                    new MarketPriceLatestResponse(
                            sym,
                            rate.buy(),
                            rate.sell(),
                            "TCMB",
                            asOf,
                            asOf,
                            PriceQuality.EXACT,
                            null,
                            null,
                            null
                    )
            );
        }
        this.rates = Map.copyOf(next);
        this.updatedAt = Instant.now();
    }

    /**
     * Snapshot hâlen TTL içindeyse haritayı kopya olarak döner; değilse boş map.
     */
    public Map<String, MarketPriceLatestResponse> copyIfFresh(Duration ttl) {
        Instant at = updatedAt;
        if (at.equals(Instant.EPOCH)) {
            return Map.of();
        }
        if (Duration.between(at, Instant.now()).compareTo(ttl) > 0) {
            return Map.of();
        }
        Map<String, MarketPriceLatestResponse> snap = rates;
        return snap.isEmpty() ? Map.of() : Map.copyOf(snap);
    }
}
