package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.application.MarketPriceQueryService;
import com.nurseli.marketdata.application.TcmbFxSnapshotCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sıcak yol: yalnızca DB + ingest tarafından doldurulan bellek snapshot.
 * Canlı TCMB HTTP çağrısı burada yapılmaz; scheduler / startup ingest üretir.
 */
@Slf4j
@Component
public class TcmbFxProvider implements FxProvider {

    private static final List<String> CORE_TRIPLET = List.of("USDTRY", "EURTRY", "GBPTRY");

    private final MarketPriceQueryService queryService;
    private final TcmbFxSnapshotCache snapshotCache;
    private final int snapshotTtlSeconds;

    public TcmbFxProvider(
            MarketPriceQueryService queryService,
            TcmbFxSnapshotCache snapshotCache,
            @Value("${app.market.fx.snapshot-ttl-seconds:90}") int snapshotTtlSeconds
    ) {
        this.queryService = queryService;
        this.snapshotCache = snapshotCache;
        this.snapshotTtlSeconds = snapshotTtlSeconds;
    }

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        Map<String, MarketPriceLatestResponse> merged = new LinkedHashMap<>(queryService.getLatestFx());
        if (hasCoreTriplet(merged)) {
            return merged;
        }
        Duration ttl = Duration.ofSeconds(Math.max(30, snapshotTtlSeconds));
        snapshotCache.copyIfFresh(ttl).forEach(merged::putIfAbsent);
        if (hasCoreTriplet(merged)) {
            return merged;
        }
        if (!merged.isEmpty()) {
            log.debug(
                    "[TCMB_PROVIDER] USD/EUR/GBP üçlüsü hâlâ eksik (DB + snapshot). TTL={}s — bir sonraki ingest bekleniyor.",
                    snapshotTtlSeconds
            );
        }
        return merged;
    }

    private static boolean hasCoreTriplet(Map<String, MarketPriceLatestResponse> m) {
        if (m == null) {
            return false;
        }
        for (String sym : CORE_TRIPLET) {
            MarketPriceLatestResponse row = m.get(sym);
            if (row == null
                    || row.buyPrice() == null
                    || row.sellPrice() == null
                    || row.buyPrice().signum() <= 0
                    || row.sellPrice().signum() <= 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        return queryService.getHistory(symbol, days);
    }

    @Override
    public String providerName() {
        return "TCMB";
    }

    @Override
    public boolean isCanonical() {
        return true;
    }
}
