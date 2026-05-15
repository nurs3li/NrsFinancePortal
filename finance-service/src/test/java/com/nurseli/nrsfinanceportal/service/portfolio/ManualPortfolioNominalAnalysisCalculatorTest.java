package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus;
import com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.LatestPricingSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualPortfolioNominalAnalysisCalculatorTest {

    @Mock
    private MarketDataClient marketDataClient;

    @InjectMocks
    private ManualPortfolioNominalAnalysisCalculator calculator;

    @Test
    void openUnrealizedUsesBuyFee() {
        User u = userStub();
        ManualPortfolioPosition p = ManualPortfolioPosition.createNew(
                u, AssetType.FX, "USDTRY", new BigDecimal("10"),
                LocalDate.of(2024, 1, 1), new BigDecimal("30"),
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 1, 1), true,
                new BigDecimal("5"),
                ManualPositionStatus.OPEN,
                null, null, null, null, false, null, null
        );
        when(marketDataClient.loadLatestPricing()).thenReturn(emptySnap());
        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(new BigDecimal("35"));

        ManualPortfolioNominalAnalysis a = calculator.compute(p);
        assertThat(a.buyCost()).isEqualByComparingTo(new BigDecimal("305"));
        assertThat(a.unrealizedProfit()).isEqualByComparingTo(new BigDecimal("45"));
    }

    @Test
    void soldRealizedAndMissed() {
        User u = userStub();
        ManualPortfolioPosition p = ManualPortfolioPosition.createNew(
                u, AssetType.FX, "USDTRY", new BigDecimal("2"),
                LocalDate.of(2024, 1, 1), new BigDecimal("10"),
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 1, 1), true,
                BigDecimal.ZERO,
                ManualPositionStatus.SOLD,
                LocalDate.of(2024, 6, 1), new BigDecimal("15"),
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 6, 1), true,
                new BigDecimal("1"),
                null
        );
        when(marketDataClient.loadLatestPricing()).thenReturn(emptySnap());
        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(new BigDecimal("20"));

        ManualPortfolioNominalAnalysis a = calculator.compute(p);
        assertThat(a.buyCost()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(a.sellProceeds()).isEqualByComparingTo(new BigDecimal("29"));
        assertThat(a.realizedProfit()).isEqualByComparingTo(new BigDecimal("9"));
        assertThat(a.holdValueToday()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(a.missedProfit()).isEqualByComparingTo(new BigDecimal("11"));
    }

    private static User userStub() {
        User u = User.createFromIdentity("kc-test", "e@e.com", "u");
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, 1L);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return u;
    }

    private static LatestPricingSnapshot emptySnap() {
        return new LatestPricingSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
