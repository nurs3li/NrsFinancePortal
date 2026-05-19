package com.nurseli.nrsfinanceportal.service.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import com.nurseli.nrsfinanceportal.domain.viop.ViopPositionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ViopPositionMetricsCalculatorTest {

    private final ViopPositionMetricsCalculator calculator = new ViopPositionMetricsCalculator();

    @Test
    void longPnlAndRisk() {
        ManualViopPosition p = openPosition(ViopDirection.LONG);
        var m = calculator.compute(p, new BigDecimal("110"), LocalDate.of(2026, 5, 19));
        assertThat(m.unrealizedPnl()).isEqualByComparingTo("1000");
        assertThat(m.riskExposure()).isEqualByComparingTo("11000");
    }

    @Test
    void shortPnl() {
        ManualViopPosition p = openPosition(ViopDirection.SHORT);
        var m = calculator.compute(p, new BigDecimal("90"), LocalDate.of(2026, 5, 19));
        assertThat(m.unrealizedPnl()).isEqualByComparingTo("1000");
    }

    @Test
    void nullCurrentPriceYieldsNullPnl() {
        ManualViopPosition p = openPosition(ViopDirection.LONG);
        var m = calculator.compute(p, null, LocalDate.of(2026, 5, 19));
        assertThat(m.unrealizedPnl()).isNull();
        assertThat(m.riskExposure()).isNull();
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
