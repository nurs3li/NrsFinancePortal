package com.nurseli.nrsfinanceportal.application.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondPositionStatus;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * finance-service bond CPI desteği — tahvil pozisyonları için TÜFE endeks aralığını market-data'dan yükler.
 */
@RequiredArgsConstructor
@Component

public class BondPositionCpiSupport {

    private final MarketDataClient marketDataClient;

    /**
     * {@code loadForPositions} — Pozisyon alış/satış tarihlerine göre gerekli CPI lookup aralığını yükler.
     */
    public CpiIndexLookup loadForPositions(List<ManualBondPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return CpiIndexLookup.empty();
    }
        LocalDate min = null;
        LocalDate max = null;
        for (ManualBondPosition p : positions) {
            if (p.getBuyDate() != null) {
                min = min == null || p.getBuyDate().isBefore(min) ? p.getBuyDate() : min;
            }
            LocalDate end = p.getStatus() == BondPositionStatus.SOLD && p.getSellDate() != null
                    ? p.getSellDate()
                    : LocalDate.now();
            max = max == null || end.isAfter(max) ? end : max;
        }
        if (min == null) {
            return CpiIndexLookup.empty();
        }
        try {
            return marketDataClient.loadCpiIndexLookup(min, max != null ? max : LocalDate.now());
        } catch (RuntimeException ignored) {
            return CpiIndexLookup.empty();
        }
    }
}
