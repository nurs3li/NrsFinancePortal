package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.nurseli.nrsfinanceportal.application.viop.ViopFxRates;
import com.nurseli.nrsfinanceportal.application.viop.ViopQuoteCurrency;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ViopPositionMetricsCalculatorTest {

    private final ViopPositionMetricsCalculator calculator = new ViopPositionMetricsCalculator();

    @Test
    void longPnlAndRisk() {
        ManualViopPosition p = openPosition(ViopDirection.LONG);
        var m = calculator.compute(p, new BigDecimal("110"), LocalDate.of(2026, 5, 19));
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("1000");
        assertThat(m.riskExposureTry()).isEqualByComparingTo("11000");
    }

    @Test
    void shortPnl() {
        ManualViopPosition p = openPosition(ViopDirection.SHORT);
        var m = calculator.compute(p, new BigDecimal("90"), LocalDate.of(2026, 5, 19));
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("1000");
    }

    @Test
    void usdQuotedExposureUsesFx() {
        ManualViopPosition p = ManualViopPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                "F_XAUUSD1026",
                "Altın Vadeli",
                ViopCategory.COMMODITY,
                "XAUUSD",
                ViopDirection.LONG,
                new BigDecimal("3"),
                new BigDecimal("4902"),
                LocalDate.of(2026, 1, 1),
                null,
                BigDecimal.ONE,
                new BigDecimal("1000"),
                LocalDate.of(2026, 10, 1),
                null);
        var fx = new ViopFxRates(new BigDecimal("34"), new BigDecimal("37"));
        var m = calculator.compute(p, new BigDecimal("4902"), LocalDate.of(2026, 5, 19), fx);
        assertThat(m.quoteCurrency()).isEqualTo(ViopQuoteCurrency.USD);
        assertThat(m.riskExposureNative()).isEqualByComparingTo("14706");
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("0");
        assertThat(m.marginTry()).isEqualByComparingTo("1000");
        assertThat(m.netFinancialEffect()).isEqualByComparingTo("1000");
        assertThat(m.riskExposureTry()).isEqualByComparingTo("500004");
        assertThat(m.missingFxRate()).isFalse();
    }

    @Test
    void nullCurrentPriceYieldsNullPnl() {
        ManualViopPosition p = openPosition(ViopDirection.LONG);
        var m = calculator.compute(p, null, LocalDate.of(2026, 5, 19));
        assertThat(m.unrealizedPnlTry()).isNull();
        assertThat(m.riskExposureTry()).isNull();
    }

    private static ManualViopPosition openPosition(ViopDirection direction) {
        return ManualViopPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                "F_USDTRY0726",
                "USDTRY Vadeli",
                ViopCategory.FX,
                "USDTRY",
                direction,
                new BigDecimal("10"),
                new BigDecimal("100"),
                LocalDate.of(2026, 1, 1),
                null,
                new BigDecimal("10"),
                new BigDecimal("5000"),
                LocalDate.of(2026, 7, 1),
                null
        );
    }
}
