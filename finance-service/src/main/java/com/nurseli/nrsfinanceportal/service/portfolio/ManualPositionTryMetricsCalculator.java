package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class ManualPositionTryMetricsCalculator {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final MarketDataClient marketDataClient;

    public PositionMarketMetrics compute(ManualPortfolioPosition p) {
        LatestPricingSnapshot snap = marketDataClient.loadLatestPricing();
        AssetType type = p.getType();
        BigDecimal qty = nz(p.getQuantity());
        BigDecimal buy = nz(p.getBuyPrice());
        BigDecimal invested = buy.multiply(qty).setScale(8, RoundingMode.HALF_UP);
        BigDecimal currentPrice = nz(marketDataClient.getPriceTry(type, p.getSymbol(), snap));
        BigDecimal currentValue = currentPrice.multiply(qty).setScale(8, RoundingMode.HALF_UP);
        BigDecimal nominal = currentValue.subtract(invested).setScale(8, RoundingMode.HALF_UP);
        Instant buyResolved = p.getBuyDate() != null
                ? p.getBuyDate().atStartOfDay(TZ).toInstant()
                : Instant.now();
        return new PositionMarketMetrics(
                invested,
                currentPrice.signum() > 0 ? currentPrice : null,
                currentValue,
                nominal,
                null,
                buyResolved
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public record PositionMarketMetrics(
            BigDecimal investedAmountTry,
            BigDecimal currentPrice,
            BigDecimal currentValueTry,
            BigDecimal nominalProfitTry,
            BigDecimal realProfitTry,
            Instant buyResolvedDate
    ) {}
}
