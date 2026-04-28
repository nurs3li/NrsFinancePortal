package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.DataQualityFlag;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.infrastructure.bist.BistViopClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase-2 adapter placeholder for real VIOP provider integration.
 * Keeps interface contract ready without breaking current MVP ingest flow.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BistViopProvider implements DerivativesProvider {
    private final BistViopClient bistViopClient;

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        Map<String, MarketPriceLatestResponse> out = new LinkedHashMap<>();
        bistViopClient.fetchLatest().forEach(r -> out.put(r.contractCode(), new MarketPriceLatestResponse(
                r.contractCode(),
                r.price(),
                r.price(),
                r.source() == null ? "BIST_VIOP" : r.source(),
                r.asOf(),
                r.asOf(),
                PriceQuality.EXACT,
                null,
                null,
                null
        )));
        return out;
    }

    @Override
    public List<MarketPriceHistoryResponse> getHistory(String symbol, int days) {
        LocalDate cutoff = LocalDate.now().minusDays(days);
        return bistViopClient.fetchLatest().stream()
                .filter(r -> symbol.equalsIgnoreCase(r.contractCode()))
                .filter(r -> r.asOf() != null && !r.asOf().toLocalDate().isBefore(cutoff))
                .map(r -> new MarketPriceHistoryResponse(
                        r.price(),
                        r.price(),
                        r.asOf(),
                        r.source() == null ? "BIST_VIOP" : r.source(),
                        r.asOf(),
                        DataQualityFlag.EXACT
                ))
                .toList();
    }

    @Override
    public String providerName() {
        return "BIST_VIOP";
    }

    @Override
    public boolean isCanonical() {
        return true;
    }

    public boolean isHealthy() {
        return !bistViopClient.fetchLatest().isEmpty();
    }
}
