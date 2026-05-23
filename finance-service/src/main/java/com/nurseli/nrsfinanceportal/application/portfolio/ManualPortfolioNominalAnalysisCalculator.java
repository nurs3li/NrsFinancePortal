package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * finance-service nominal analiz hesaplayıcı — tek manuel pozisyon için maliyet, değer ve K/Z metriklerini hesaplar.
 */
@RequiredArgsConstructor
@Component

public class ManualPortfolioNominalAnalysisCalculator {

    private static final int MONEY_SCALE = 8;
    private static final int PCT_SCALE = 6;

    private final MarketDataClient marketDataClient;

    /**
     * {@code compute} — Güncel piyasa fiyatıyla pozisyon nominal analiz DTO'sunu üretir.
     */
    public ManualPortfolioNominalAnalysis compute(ManualPortfolioPosition p) {
        LatestPricingSnapshot snap = marketDataClient.loadLatestPricing();
        BigDecimal currentPrice = marketDataClient.getPriceTry(p.getType(), p.getSymbol(), snap);
    if (currentPrice == null || currentPrice.signum() <= 0) {
            currentPrice = null;
        }
        return computeWithCurrentPrice(p, currentPrice);
    }

    /**
     * {@code computeWithCurrentPrice} — Verilen güncel TRY fiyatıyla nominal analiz hesaplar.
     */
    public ManualPortfolioNominalAnalysis computeWithCurrentPrice(ManualPortfolioPosition p, BigDecimal currentPriceTry) {
        BigDecimal qty = nz(p.getQuantity());
        BigDecimal buyFee = feeNz(p.getBuyFee());
    BigDecimal sellFee = feeNz(p.getSellFee());
        BigDecimal buyCost = nz(p.getBuyPrice()).multiply(qty).add(buyFee).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal currentPrice = currentPriceTry != null && currentPriceTry.signum() > 0 ? currentPriceTry : null;
        BigDecimal currentValue = currentPrice != null
                ? currentPrice.multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : null;

        if (p.getStatus() == ManualPositionStatus.OPEN) {
            BigDecimal unrealized = currentValue != null
                    ? currentValue.subtract(buyCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                    : null;
            BigDecimal unrealizedPct = pct(unrealized, buyCost);
            return new ManualPortfolioNominalAnalysis(
                    buyCost,
                    currentPrice,
                    currentValue,
                    unrealized,
                    unrealizedPct,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    unrealized,
                    unrealizedPct
            );
        }

        BigDecimal sellProceeds = nz(p.getSellPrice()).multiply(qty).subtract(sellFee).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal realized = sellProceeds.subtract(buyCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal realizedPct = pct(realized, buyCost);

        BigDecimal holdValueToday = currentValue;
        BigDecimal holdProfitToday = holdValueToday != null
                ? holdValueToday.subtract(buyCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal holdReturnPctToday = pct(holdProfitToday, buyCost);

        BigDecimal missedProfit = (holdValueToday != null)
                ? holdValueToday.subtract(sellProceeds).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : null;
        BigDecimal missedReturnPct = pctMissed(missedProfit, sellProceeds);

        return new ManualPortfolioNominalAnalysis(
                buyCost,
                currentPrice,
                currentValue,
                null,
                null,
                sellProceeds,
                realized,
                realizedPct,
                holdValueToday,
                holdProfitToday,
                holdReturnPctToday,
                missedProfit,
                missedReturnPct,
                realized,
                realizedPct
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal feeNz(BigDecimal v) {
        if (v == null || v.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return v;
    }

    private static BigDecimal pct(BigDecimal profit, BigDecimal base) {
        if (profit == null || base == null || base.signum() <= 0) {
            return null;
        }
        return profit.divide(base, PCT_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal pctMissed(BigDecimal missedProfit, BigDecimal sellProceeds) {
        if (missedProfit == null || sellProceeds == null || sellProceeds.signum() <= 0) {
            return null;
        }
        return missedProfit.divide(sellProceeds, PCT_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }
}
