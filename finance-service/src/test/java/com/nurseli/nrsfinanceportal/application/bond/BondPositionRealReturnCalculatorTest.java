package com.nurseli.nrsfinanceportal.application.bond;

import com.nurseli.nrsfinanceportal.domain.bond.BondType;
import com.nurseli.nrsfinanceportal.domain.bond.CouponFrequency;
import com.nurseli.nrsfinanceportal.domain.bond.ManualBondPosition;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.CpiIndexLookup;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class BondPositionRealReturnCalculatorTest {

    private final BondPositionRealReturnCalculator calculator = new BondPositionRealReturnCalculator();

    @Test
    void fisherRealReturnUsesPeriodInflationNotAnnualYoY() {
        ManualBondPosition p = ManualBondPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                "TRT030129K28",
                "DİBS",
                BondType.GOVERNMENT_BOND,
                "TRY",
                new BigDecimal("10000"),
                new BigDecimal("100"),
                LocalDate.of(2026, 2, 3),
                new BigDecimal("105.85"),
                LocalDate.of(2029, 1, 3),
                new BigDecimal("10"),
                CouponFrequency.SEMI_ANNUAL,
                null
        );
        CpiIndexLookup cpi = cpiLookup(Map.of(
                LocalDate.of(2026, 2, 1), new BigDecimal("3000"),
                LocalDate.of(2026, 4, 1), new BigDecimal("3100")
        ));
        BigDecimal buyValue = new BigDecimal("10000");
        BigDecimal totalReturn = new BigDecimal("585");

        var rr = calculator.compute(p, LocalDate.of(2026, 5, 22), buyValue, totalReturn, cpi);

        assertThat(rr.available()).isTrue();
        assertThat(rr.periodInflationPercent()).isEqualByComparingTo("3.3333");
        assertThat(rr.periodNominalReturnPercent()).isEqualByComparingTo("5.8500");
        assertThat(rr.periodRealReturnPercent()).isEqualByComparingTo("2.4355");
        assertThat(rr.periodRealReturnPercent().compareTo(new BigDecimal("5.85").subtract(new BigDecimal("32.37"))))
                .isNotEqualTo(0);
    }

    @Test
    void missingCpiReturnsUnavailable() {
        ManualBondPosition p = ManualBondPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                "TRX",
                null,
                BondType.GOVERNMENT_BOND,
                "TRY",
                new BigDecimal("10000"),
                new BigDecimal("100"),
                LocalDate.of(2020, 1, 1),
                new BigDecimal("100"),
                null,
                null,
                CouponFrequency.NONE,
                null
        );
        var rr = calculator.compute(p, LocalDate.now(), new BigDecimal("10000"), BigDecimal.ZERO, CpiIndexLookup.empty());
        assertThat(rr.available()).isFalse();
        assertThat(rr.periodRealReturnPercent()).isNull();
    }

    private static CpiIndexLookup cpiLookup(Map<LocalDate, BigDecimal> months) {
        NavigableMap<LocalDate, BigDecimal> map = new TreeMap<>(months);
        return new CpiIndexLookup(map);
    }
}
