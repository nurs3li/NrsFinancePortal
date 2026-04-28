package com.nurseli.marketdata.application.provider;

import com.nurseli.marketdata.api.dto.MarketPriceHistoryResponse;
import com.nurseli.marketdata.api.dto.MarketPriceLatestResponse;
import com.nurseli.marketdata.api.dto.DataQualityFlag;
import com.nurseli.marketdata.api.dto.PriceQuality;
import com.nurseli.marketdata.infrastructure.debt.DebtMarketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase-2 adapter placeholder for real Debt/Bond provider integration.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DebtCatalogProvider implements DebtProvider {
    private final DebtMarketClient debtMarketClient;

    @Override
    public Map<String, MarketPriceLatestResponse> getLatest() {
        Map<String, MarketPriceLatestResponse> out = new LinkedHashMap<>();
        debtMarketClient.fetchLatest().forEach(r -> out.put(r.isin(), new MarketPriceLatestResponse(
                r.isin(),
                r.dirtyPrice(),
                r.dirtyPrice(),
                r.source() == null ? "DEBT_PROVIDER" : r.source(),
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
        return debtMarketClient.fetchLatest().stream()
                .filter(r -> symbol.equalsIgnoreCase(r.isin()))
                .filter(r -> r.asOf() != null && !r.asOf().toLocalDate().isBefore(cutoff))
                .map(r -> new MarketPriceHistoryResponse(
                        r.dirtyPrice(),
                        r.dirtyPrice(),
                        r.asOf(),
                        r.source() == null ? "DEBT_PROVIDER" : r.source(),
                        r.asOf(),
                        DataQualityFlag.EXACT
                ))
                .toList();
    }

    @Override
    public String providerName() {
        return "DEBT_CATALOG";
    }

    @Override
    public boolean isCanonical() {
        return true;
    }

    public boolean isHealthy() {
        return !debtMarketClient.fetchLatest().isEmpty();
    }
}
