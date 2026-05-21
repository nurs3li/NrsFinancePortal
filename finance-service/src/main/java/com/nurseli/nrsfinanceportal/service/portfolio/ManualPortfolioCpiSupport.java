package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ManualPortfolioCpiSupport {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final MarketDataClient marketDataClient;

    public CpiIndexLookup loadForPositions(List<ManualPortfolioPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return CpiIndexLookup.empty();
        }
        LocalDate today = LocalDate.now(TZ);
        LocalDate min = null;
        LocalDate max = today;
        for (ManualPortfolioPosition p : positions) {
            if (p.getBuyDate() != null) {
                min = min == null || p.getBuyDate().isBefore(min) ? p.getBuyDate() : min;
            }
            if (p.getStatus() == ManualPositionStatus.SOLD && p.getSellDate() != null) {
                max = p.getSellDate().isAfter(max) ? p.getSellDate() : max;
            }
        }
        if (min == null) {
            return CpiIndexLookup.empty();
        }
        return marketDataClient.loadCpiIndexLookup(min, max);
    }
}
