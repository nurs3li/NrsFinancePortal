package com.nurseli.marketdata.application.deposit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositReturnCalculatorTest {

    @Test
    void simpleMaturity_withoutTax() {
        DepositReturnCalculator.DepositReturnResult r = DepositReturnCalculator.simpleMaturity(
                new BigDecimal("100000"),
                "TRY",
                "3M",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 4, 1),
                new BigDecimal("40"),
                null
        );
        assertEquals(90, r.dayCount());
        assertTrue(r.grossInterest().signum() > 0);
        assertEquals(0, r.grossInterest().compareTo(r.netInterest()));
        assertTrue(r.maturityAmount().compareTo(new BigDecimal("100000")) > 0);
    }

    @Test
    void simpleMaturity_withWithholdingFraction() {
        DepositReturnCalculator.DepositReturnResult r = DepositReturnCalculator.simpleMaturity(
                new BigDecimal("10000"),
                "TRY",
                "1M",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("36.5"),
                new BigDecimal("0.10")
        );
        assertEquals(30, r.dayCount());
        assertTrue(r.netInterest().compareTo(r.grossInterest()) < 0);
    }

    @Test
    void rejectsInvalidTax() {
        assertThrows(IllegalArgumentException.class, () -> DepositReturnCalculator.simpleMaturity(
                BigDecimal.ONE, "TRY", "1M",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                new BigDecimal("10"),
                new BigDecimal("1.5")
        ));
    }
}
