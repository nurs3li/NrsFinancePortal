package com.nurseli.nrsfinanceportal.application.portfolio;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionRealReturnCalculationMode;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionRealReturnStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * finance-service manuel portfolio reel getiri hesaplayıcı — açık ve kapalı pozisyonlar için TÜFE düzeltmeli reel getiri üretir.
 */
@RequiredArgsConstructor
@Component

public class ManualPortfolioRealReturnCalculator {

    private static final int MONEY_SCALE = 8;
    private static final int PCT_SCALE = 6;

    private final MarketDataClient marketDataClient;
    private final ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;

    /**
     * PositionRealReturn — Tek pozisyon reel getiri satırı — maliyet, değer ve reel K/Z alanlarını taşır.
     */
    public record PositionRealReturn(
            BigDecimal nominalCost,
            BigDecimal exitValue,
            BigDecimal nominalProfit,
            BigDecimal nominalReturnPct,
            BigDecimal inflationFactor,
            BigDecimal inflationReturnPct,
            BigDecimal inflationAdjustedCost,
            BigDecimal realProfit,
            BigDecimal realReturnPct,
            boolean realReturnAvailable,
            ManualPositionRealReturnStatus realReturnStatus,
            LocalDate cpiStartDate,
            LocalDate cpiEndDate,
            BigDecimal cpiStartValue,
            BigDecimal cpiEndValue,
            LocalDate calculationEndDate,
            ManualPositionRealReturnCalculationMode calculationMode
    ) {}

    /**
     * PortfolioRealReturnResult — Portfolio düzeyinde reel getiri özeti — toplam reel K/Z ve getiri yüzdesini taşır.
     */
    public record PortfolioRealReturnResult(
    BigDecimal openCurrentValue,
            BigDecimal closedRealizedValue,
            BigDecimal totalEvaluatedValue,
            BigDecimal totalInvestedAmount,
            BigDecimal nominalReturn,
            BigDecimal nominalReturnPct,
            BigDecimal inflationAdjustedCost,
            BigDecimal realReturn,
            BigDecimal realReturnPct,
            boolean realReturnAvailable,
            String realReturnUnavailableReason,
            List<PositionRealReturn> positions
    ) {}

    /**
     * {@code compute} — CPI lookup ve güncel fiyatlarla portfolio düzeyinde reel getiri sonucunu hesaplar.
     */
    public PortfolioRealReturnResult compute(
            List<ManualPortfolioPosition> positions,
            CpiIndexLookup cpiLookup,
    LatestPricingSnapshot pricing) {

        if (positions == null || positions.isEmpty()) {
            return emptyResult();
        }

        BigDecimal openCurrentValue = BigDecimal.ZERO;
        BigDecimal closedRealizedValue = BigDecimal.ZERO;
        BigDecimal totalNominalCost = BigDecimal.ZERO;
        BigDecimal totalExitValue = BigDecimal.ZERO;
        BigDecimal totalNominalProfit = BigDecimal.ZERO;
        BigDecimal totalInflationAdjustedCost = BigDecimal.ZERO;
        boolean anyRealIncluded = false;
        List<PositionRealReturn> perPosition = new ArrayList<>();

        for (ManualPortfolioPosition p : positions) {
            BigDecimal priceTry = marketDataClient.getPriceTry(p.getType(), p.getSymbol(), pricing);
            ManualPortfolioNominalAnalysis nominal = nominalAnalysisCalculator.computeWithCurrentPrice(p, priceTry);
            if (nominal == null) {
                perPosition.add(new PositionRealReturn(
                        BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                        null, null, null, null, null,
                        false, ManualPositionRealReturnStatus.NO_CPI_DATA,
                        null, null, null, null, null, null));
                continue;
            }

            BigDecimal nominalCost = nz(nominal.buyCost());
            totalNominalCost = totalNominalCost.add(nominalCost);

            BigDecimal exitValue;
            BigDecimal nominalProfit;
            ManualPositionRealReturnCalculationMode mode;
            LocalDate calculationEndDate;

            if (p.getStatus() == ManualPositionStatus.OPEN) {
                exitValue = nominal.currentValue() != null ? nominal.currentValue() : BigDecimal.ZERO;
                openCurrentValue = openCurrentValue.add(exitValue);
                nominalProfit = nominal.unrealizedProfit() != null ? nominal.unrealizedProfit() : BigDecimal.ZERO;
                mode = ManualPositionRealReturnCalculationMode.OPEN_POSITION_MARK_TO_MARKET;
                calculationEndDate = cpiLookup.latestMonth().orElse(null);
            } else {
                BigDecimal qty = nz(p.getQuantity());
                exitValue = nz(p.getSellPrice()).multiply(qty).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                closedRealizedValue = closedRealizedValue.add(exitValue);
                nominalProfit = nominal.realizedProfit() != null ? nominal.realizedProfit() : BigDecimal.ZERO;
                mode = ManualPositionRealReturnCalculationMode.SOLD_POSITION;
                calculationEndDate = p.getSellDate();
            }

            totalExitValue = totalExitValue.add(exitValue);
            totalNominalProfit = totalNominalProfit.add(nominalProfit);

            Optional<LocalDate> cpiStartMonth = cpiLookup.monthAtOrBefore(p.getBuyDate());
            Optional<BigDecimal> buyCpi = cpiStartMonth.flatMap(m -> cpiLookup.indexAtOrBefore(m));
            Optional<LocalDate> cpiEndMonth;
            Optional<BigDecimal> endCpi;
            if (p.getStatus() == ManualPositionStatus.OPEN) {
                cpiEndMonth = cpiLookup.latestMonth();
                endCpi = cpiLookup.latestIndex();
            } else {
                LocalDate sellDate = p.getSellDate() != null ? p.getSellDate() : LocalDate.now();
                cpiEndMonth = cpiLookup.monthAtOrBefore(sellDate);
                endCpi = cpiEndMonth.flatMap(m -> cpiLookup.indexAtOrBefore(m));
            }

            if (cpiStartMonth.isEmpty() || buyCpi.isEmpty() || cpiEndMonth.isEmpty() || endCpi.isEmpty()) {
                perPosition.add(new PositionRealReturn(
                        nominalCost,
                        exitValue,
                        nominalProfit,
                        pct(nominalProfit, nominalCost),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        ManualPositionRealReturnStatus.NO_CPI_DATA,
                        null,
                        null,
                        null,
                        null,
                        calculationEndDate,
                        mode
                ));
                continue;
            }

            BigDecimal inflationFactor = endCpi.get().divide(buyCpi.get(), MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal inflationReturnPct = inflationFactor.subtract(BigDecimal.ONE)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(PCT_SCALE, RoundingMode.HALF_UP);
            BigDecimal inflationAdjustedCost = nominalCost.multiply(inflationFactor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal realProfit = exitValue.subtract(inflationAdjustedCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal realReturnPct = returnPctFromRatio(exitValue, inflationAdjustedCost);

            totalInflationAdjustedCost = totalInflationAdjustedCost.add(inflationAdjustedCost);
            anyRealIncluded = true;

            ManualPositionRealReturnStatus status = realReturnPct != null
                    && realReturnPct.compareTo(BigDecimal.ZERO) >= 0
                    ? ManualPositionRealReturnStatus.BEAT_INFLATION
                    : ManualPositionRealReturnStatus.LOST_TO_INFLATION;

            perPosition.add(new PositionRealReturn(
                    nominalCost,
                    exitValue,
                    nominalProfit,
                    pct(nominalProfit, nominalCost),
                    inflationFactor,
                    inflationReturnPct,
                    inflationAdjustedCost,
                    realProfit,
                    realReturnPct,
                    true,
                    status,
                    cpiStartMonth.get(),
                    cpiEndMonth.get(),
                    buyCpi.get(),
                    endCpi.get(),
                    calculationEndDate,
                    mode
            ));
        }

        BigDecimal totalEvaluated = openCurrentValue.add(closedRealizedValue);
        BigDecimal nominalReturnPct = pct(totalNominalProfit, totalNominalCost);

        if (!anyRealIncluded) {
            String reason = !cpiLookup.isAvailable()
                    ? "TÜFE endeks geçmişi market-data üzerinden alınamadı."
                    : "Bazı pozisyonlar için alım/satış dönemine ait TÜFE endeksi çözümlenemedi.";
            return new PortfolioRealReturnResult(
                    openCurrentValue,
                    closedRealizedValue,
                    totalEvaluated,
                    totalNominalCost,
                    totalNominalProfit,
                    nominalReturnPct,
                    null,
                    null,
                    null,
                    false,
                    reason,
                    perPosition
            );
        }

        BigDecimal totalRealProfit = totalExitValue.subtract(totalInflationAdjustedCost)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal portfolioRealReturnPct = returnPctFromRatio(totalExitValue, totalInflationAdjustedCost);

        return new PortfolioRealReturnResult(
                openCurrentValue,
                closedRealizedValue,
                totalEvaluated,
                totalNominalCost,
                totalNominalProfit,
                nominalReturnPct,
                totalInflationAdjustedCost,
                totalRealProfit,
                portfolioRealReturnPct,
                true,
                null,
                perPosition
        );
    }

    private static PortfolioRealReturnResult emptyResult() {
        return new PortfolioRealReturnResult(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                null,
                false,
                "Manuel portföy kaydı bulunmuyor.",
                List.of()
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal pct(BigDecimal profit, BigDecimal base) {
        if (profit == null || base == null || base.signum() <= 0) {
            return null;
        }
        return profit.divide(base, PCT_SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal returnPctFromRatio(BigDecimal exitValue, BigDecimal inflationAdjustedCost) {
        if (exitValue == null || inflationAdjustedCost == null || inflationAdjustedCost.signum() <= 0) {
            return null;
        }
        return exitValue.divide(inflationAdjustedCost, PCT_SCALE + 2, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(PCT_SCALE, RoundingMode.HALF_UP);
    }
}
