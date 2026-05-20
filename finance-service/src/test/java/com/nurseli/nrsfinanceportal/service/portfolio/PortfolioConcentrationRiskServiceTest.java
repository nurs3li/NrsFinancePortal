package com.nurseli.nrsfinanceportal.service.portfolio;

import com.nurseli.nrsfinanceportal.common.dto.ManualPortfolioNominalAnalysis;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioConcentrationRiskDto;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioConcentrationRiskServiceTest {

    @Mock
    private ManualPortfolioNominalAnalysisCalculator nominalAnalysisCalculator;

    @Mock
    private MarketDataClient marketDataClient;

    @InjectMocks
    private PortfolioConcentrationRiskService service;

    @Test
    void highWhenTopAbove60() {
        User u = userStub();
        ManualPortfolioPosition big = open(u, "BTC", new BigDecimal("700"));
        ManualPortfolioPosition small = open(u, "ETH", new BigDecimal("300"));
        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(BigDecimal.ONE);
        when(nominalAnalysisCalculator.computeWithCurrentPrice(eq(big), any()))
                .thenReturn(analysis(new BigDecimal("700")));
        when(nominalAnalysisCalculator.computeWithCurrentPrice(eq(small), any()))
                .thenReturn(analysis(new BigDecimal("300")));

        PortfolioConcentrationRiskDto dto = service.evaluate(List.of(big, small), emptySnap());

        assertThat(dto.riskLevel()).isEqualTo("HIGH");
        assertThat(dto.topAssetWeightPct()).isEqualByComparingTo(new BigDecimal("70.0000"));
    }

    @Test
    void mediumWhenTopBetween40And60() {
        User u = userStub();
        ManualPortfolioPosition a = open(u, "A", new BigDecimal("50"));
        ManualPortfolioPosition b = open(u, "B", new BigDecimal("50"));
        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(BigDecimal.ONE);
        when(nominalAnalysisCalculator.computeWithCurrentPrice(eq(a), any()))
                .thenReturn(analysis(new BigDecimal("50")));
        when(nominalAnalysisCalculator.computeWithCurrentPrice(eq(b), any()))
                .thenReturn(analysis(new BigDecimal("50")));

        PortfolioConcentrationRiskDto dto = service.evaluate(List.of(a, b), emptySnap());

        assertThat(dto.riskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void lowWhenBalanced() {
        User u = userStub();
        ManualPortfolioPosition a = open(u, "A", new BigDecimal("34"));
        ManualPortfolioPosition b = open(u, "B", new BigDecimal("33"));
        ManualPortfolioPosition c = open(u, "C", new BigDecimal("33"));
        when(marketDataClient.getPriceTry(any(), any(), any())).thenReturn(BigDecimal.ONE);
        when(nominalAnalysisCalculator.computeWithCurrentPrice(any(), any()))
                .thenAnswer(inv -> analysis(((ManualPortfolioPosition) inv.getArgument(0)).getSymbol().equals("A")
                        ? new BigDecimal("34") : new BigDecimal("33")));

        PortfolioConcentrationRiskDto dto = service.evaluate(List.of(a, b, c), emptySnap());

        assertThat(dto.riskLevel()).isEqualTo("LOW");
    }

    private static ManualPortfolioNominalAnalysis analysis(BigDecimal currentValue) {
        return new ManualPortfolioNominalAnalysis(
                null, null, currentValue, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static LatestPricingSnapshot emptySnap() {
        return new LatestPricingSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static ManualPortfolioPosition open(User u, String symbol, BigDecimal ignored) {
        return ManualPortfolioPosition.createNew(
                u, AssetType.CRYPTO, symbol, BigDecimal.ONE,
                LocalDate.of(2024, 1, 1), BigDecimal.TEN,
                ManualPriceSource.USER_INPUT, LocalDate.of(2024, 1, 1), true,
                BigDecimal.ZERO,
                ManualPositionStatus.OPEN,
                null, null, null, null, false, null, null
        );
    }

    private static User userStub() {
        return User.createFromIdentity("kc-test", "e@e.com", "u");
    }
}
