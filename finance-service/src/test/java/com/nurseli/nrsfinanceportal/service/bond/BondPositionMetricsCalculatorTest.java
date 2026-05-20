package com.nurseli.nrsfinanceportal.service.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondType;
import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BondPositionMetricsCalculatorTest {

    private final BondPositionMetricsCalculator calculator = new BondPositionMetricsCalculator();

    @Test
    void computesValueAndPnl() {
        ManualBondPosition p = ManualBondPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                "TR2034",
                "TR 2034",
                BondType.EUROBOND,
                "USD",
                new BigDecimal("100000"),
                new BigDecimal("95"),
                LocalDate.of(2024, 1, 1),
                new BigDecimal("100"),
                LocalDate.of(2034, 1, 1),
                new BigDecimal("8.5"),
                CouponFrequency.SEMI_ANNUAL,
                null
        );
        var m = calculator.compute(p, LocalDate.of(2026, 5, 19));
        assertThat(m.buyValue()).isEqualByComparingTo("95000");
        assertThat(m.currentValue()).isEqualByComparingTo("100000");
        assertThat(m.pnl()).isEqualByComparingTo("5000");
        assertThat(m.returnPct()).isEqualByComparingTo("5.2632");
        assertThat(m.annualCoupon()).isEqualByComparingTo("8500");
    }

    @Test
    void nullCurrentPriceYieldsNullValue() {
        ManualBondPosition p = ManualBondPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                "TR2034",
                null,
                BondType.GOVERNMENT_BOND,
                "TRY",
                new BigDecimal("50000"),
                new BigDecimal("100"),
                LocalDate.of(2025, 1, 1),
                null,
                null,
                null,
                CouponFrequency.NONE,
                null
        );
        var m = calculator.compute(p, LocalDate.now());
        assertThat(m.currentValue()).isNull();
        assertThat(m.pnl()).isNull();
    }
}
