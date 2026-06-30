package com.nurseli.nrsfinanceportal.application.viop;

import com.nurseli.nrsfinanceportal.domain.viop.ManualViopPosition;
import com.nurseli.nrsfinanceportal.domain.viop.ViopCategory;
import com.nurseli.nrsfinanceportal.domain.viop.ViopDirection;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ViopPositionMetricsCalculatorTest {

    private final ViopPositionMetricsCalculator calculator = new ViopPositionMetricsCalculator();

    @Test
    void equityMultiplierAppliedToPnlAndRisk() {
        // F_USDTRY0726 FX -> çarpan kayıttan değil, resolver ile 1000.
        ManualViopPosition p = openPosition(ViopDirection.LONG);
        var m = calculator.compute(p, new BigDecimal("110"), LocalDate.of(2026, 5, 19));
        // pnl = (110-100) * 1000 * 10 = 100000
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("100000");
        // exposure = 110 * 1000 * 10 = 1.100.000
        assertThat(m.riskExposureTry()).isEqualByComparingTo("1100000");
        // toplam teminat = 5000 (tek sözleşme) * 10 (adet) = 50000
        assertThat(m.marginTry()).isEqualByComparingTo("50000");
    }

    @Test
    void shortPnl() {
        ManualViopPosition p = openPosition(ViopDirection.SHORT);
        var m = calculator.compute(p, new BigDecimal("90"), LocalDate.of(2026, 5, 19));
        // pnl = (100-90) * 1000 * 10 = 100000
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("100000");
    }

    @Test
    void usdQuotedGoldUsesFxAndOunceMultiplier() {
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
                BigDecimal.ONE, // kayıttaki çarpan yok sayılır
                new BigDecimal("1000"),
                LocalDate.of(2026, 10, 1),
                null);
        var fx = new ViopFxRates(new BigDecimal("34"), new BigDecimal("37"));
        var m = calculator.compute(p, new BigDecimal("4902"), LocalDate.of(2026, 5, 19), fx);
        assertThat(m.quoteCurrency()).isEqualTo(ViopQuoteCurrency.USD);
        // ons altın çarpanı 1 -> riskNative = 4902 * 1 * 3 = 14.706
        assertThat(m.riskExposureNative()).isEqualByComparingTo("14706");
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("0");
        // toplam teminat = 1000 * 3 = 3000 (TRY)
        assertThat(m.marginTry()).isEqualByComparingTo("3000");
        assertThat(m.netFinancialEffect()).isEqualByComparingTo("3000");
        // riskTry = 14.706 * 34 = 500.004
        assertThat(m.riskExposureTry()).isEqualByComparingTo("500004");
        assertThat(m.missingFxRate()).isFalse();
    }

    @Test
    void equityLongExampleSise() {
        ManualViopPosition p = equityPosition("F_SISE0726", "SISE", ViopDirection.LONG,
                "70", "48.21");
        var m = calculator.compute(p, new BigDecimal("46.57"), LocalDate.of(2026, 6, 28));
        // exposure = 46.57 * 100 * 70 = 325.990
        assertThat(m.riskExposureTry()).isEqualByComparingTo("325990");
        // K/Z = (46.57 - 48.21) * 100 * 70 = -11.480
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("-11480");
    }

    @Test
    void equityShortExampleAkbnk() {
        ManualViopPosition p = equityPosition("F_AKBNK0726", "AKBNK", ViopDirection.SHORT,
                "100", "67.52");
        var m = calculator.compute(p, new BigDecimal("79.60"), LocalDate.of(2026, 6, 28));
        // exposure = 79.60 * 100 * 100 = 796.000
        assertThat(m.riskExposureTry()).isEqualByComparingTo("796000");
        // K/Z = (67.52 - 79.60) * 100 * 100 = -120.800
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("-120800");
    }

    @Test
    void indexShortExampleXu030() {
        ManualViopPosition p = ManualViopPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                "F_XU0301226",
                "BIST 30 Vadeli",
                ViopCategory.INDEX,
                "XU030",
                ViopDirection.SHORT,
                new BigDecimal("10"),
                new BigDecimal("19755"),
                LocalDate.of(2026, 4, 22),
                null,
                BigDecimal.ONE,
                new BigDecimal("21420"),
                LocalDate.of(2026, 12, 1),
                null);
        var m = calculator.compute(p, new BigDecimal("19008"), LocalDate.of(2026, 6, 28));
        // endeks çarpanı 10 -> exposure = 19008 * 10 * 10 = 1.900.800
        assertThat(m.riskExposureTry()).isEqualByComparingTo("1900800");
        // K/Z = (19755 - 19008) * 10 * 10 = 74.700
        assertThat(m.unrealizedPnlTry()).isEqualByComparingTo("74700");
    }

    @Test
    void nullCurrentPriceYieldsNullPnl() {
        ManualViopPosition p = openPosition(ViopDirection.LONG);
        var m = calculator.compute(p, null, LocalDate.of(2026, 5, 19));
        assertThat(m.unrealizedPnlTry()).isNull();
        assertThat(m.riskExposureTry()).isNull();
    }

    private static ManualViopPosition equityPosition(
            String symbol, String underlying, ViopDirection direction, String count, String entry) {
        return ManualViopPosition.createNew(
                Mockito.mock(com.nurseli.nrsfinanceportal.domain.user.User.class),
                symbol,
                symbol,
                ViopCategory.EQUITY,
                underlying,
                direction,
                new BigDecimal(count),
                new BigDecimal(entry),
                LocalDate.of(2026, 6, 1),
                null,
                BigDecimal.ONE,
                new BigDecimal("600"),
                LocalDate.of(2026, 7, 1),
                null);
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
                BigDecimal.ONE,
                new BigDecimal("5000"),
                LocalDate.of(2026, 7, 1),
                null
        );
    }
}
