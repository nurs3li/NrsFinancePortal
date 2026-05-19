package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualPortfolioRealReturnCalculatorTest {

    @Mock
    private MarketDataClient marketDataClient;

    @Mock
    private ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;

    @InjectMocks
    private ManualPortfolioRealReturnCalculator calculator;

    @Test
    void openPositionNegativeRealReturn() {
        User u = userStub();
        ManualPortfolioPosition open = ManualPortfolioPosition.createNew(
                u, AssetType.FX, "USDTRY", BigDecimal.ONE,
                LocalDate.of(2024, 1, 1), new BigDecimal("100"),
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 1, 1), true,
                BigDecimal.ZERO,
                ManualPositionStatus.OPEN,
                null, null, null, null, false, null, null
        );
        CpiIndexLookup cpi = cpiLookup(
                Map.of(
                        LocalDate.of(2024, 1, 1), new BigDecimal("100"),
                        LocalDate.of(2024, 6, 1), new BigDecimal("200")
                ));
        LatestPricingSnapshot snap = emptySnap();

        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(new BigDecimal("100"));
        when(nominalAnalysisCalculator.computeWithCurrentPrice(any(), any()))
                .thenReturn(new ManualPortfolioNominalAnalysis(
                        new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"),
                        BigDecimal.ZERO, null, null, null, null, null, null, null, null, null, null, null));

        var result = calculator.compute(List.of(open), cpi, snap);

        assertThat(result.realReturnAvailable()).isTrue();
        assertThat(result.realReturn()).isNotNull();
        assertThat(result.realReturn().compareTo(BigDecimal.ZERO)).isLessThan(0);
    }

    @Test
    void soldPositionPositiveRealReturn() {
        User u = userStub();
        ManualPortfolioPosition sold = ManualPortfolioPosition.createNew(
                u, AssetType.FX, "USDTRY", new BigDecimal("1"),
                LocalDate.of(2024, 1, 1), new BigDecimal("10"),
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 1, 1), true,
                BigDecimal.ZERO,
                ManualPositionStatus.SOLD,
                LocalDate.of(2024, 6, 1), new BigDecimal("20"),
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 6, 1), true,
                BigDecimal.ZERO,
                null
        );
        CpiIndexLookup cpi = cpiLookup(
                Map.of(
                        LocalDate.of(2024, 1, 1), new BigDecimal("100"),
                        LocalDate.of(2024, 6, 1), new BigDecimal("110")
                ));
        LatestPricingSnapshot snap = emptySnap();

        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(new BigDecimal("25"));
        when(nominalAnalysisCalculator.computeWithCurrentPrice(eq(sold), any()))
                .thenReturn(new ManualPortfolioNominalAnalysis(
                        new BigDecimal("10"), null, null, null, null,
                        new BigDecimal("20"), new BigDecimal("10"), null,
                        null, null, null, null, null, null, null));

        var result = calculator.compute(List.of(sold), cpi, snap);

        assertThat(result.realReturnAvailable()).isTrue();
        assertThat(result.realReturn()).isNotNull();
        assertThat(result.realReturn().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    void cpiUnavailableReturnsFalse() {
        User u = userStub();
        ManualPortfolioPosition open = position(u, ManualPositionStatus.OPEN, LocalDate.of(2024, 1, 1), null);
        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(new BigDecimal("10"));
        when(nominalAnalysisCalculator.computeWithCurrentPrice(any(), any()))
                .thenReturn(new ManualPortfolioNominalAnalysis(
                        new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("10"),
                        BigDecimal.ZERO, null, null, null, null, null, null, null, null, null, null, null));
        var result = calculator.compute(List.of(open), CpiIndexLookup.empty(), emptySnap());

        assertThat(result.realReturnAvailable()).isFalse();
        assertThat(result.realReturn()).isNull();
    }

    private static ManualPortfolioPosition position(
            User u, ManualPositionStatus status, LocalDate buy, LocalDate sell) {
        return ManualPortfolioPosition.createNew(
                u, AssetType.FX, "USDTRY", BigDecimal.ONE,
                buy, new BigDecimal("10"),
                ManualPriceSource.USER_INPUT, buy, true,
                BigDecimal.ZERO,
                status,
                sell, sell != null ? new BigDecimal("15") : null,
                ManualPriceSource.USER_INPUT, sell, true,
                BigDecimal.ZERO,
                null
        );
    }

    private static CpiIndexLookup cpiLookup(Map<LocalDate, BigDecimal> months) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>(months);
        return new CpiIndexLookup(map);
    }

    private static User userStub() {
        User u = User.createFromIdentity("kc-test", "e@e.com", "u");
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, 1L);
        } catch (ReflectiveOperationException ignored) {
        }
        return u;
    }

    private static LatestPricingSnapshot emptySnap() {
        return new LatestPricingSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
