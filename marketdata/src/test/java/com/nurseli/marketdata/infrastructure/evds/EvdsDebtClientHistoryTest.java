package com.nurseli.marketdata.infrastructure.evds;

import com.nurseli.marketdata.config.EvdsProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvdsDebtClientHistoryTest {

    @Test
    void mergeHistoryRows_mergesPriceAndCouponSeriesByDay() {
        EvdsProperties.Instrument instrument = instrument("TRT170227K64");

        List<EvdsDebtClient.EvdsDebtRow> rows = EvdsDebtClient.mergeHistoryRows(
                instrument,
                List.of(
                        point(LocalDate.of(2026, 5, 23), "14.667"),
                        point(LocalDate.of(2026, 5, 24), "14.683")),
                List.of(
                        point(LocalDate.of(2026, 5, 23), "17.2"),
                        point(LocalDate.of(2026, 5, 24), "17.3")));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).dirtyPrice()).isEqualByComparingTo("14.667");
        assertThat(rows.get(0).couponRate()).isEqualByComparingTo("17.2");
        assertThat(rows.get(1).asOf()).isEqualTo(LocalDate.of(2026, 5, 24).atStartOfDay());
    }

    @Test
    void mergeHistoryRows_skipsDaysWithoutPrice() {
        EvdsProperties.Instrument instrument = instrument("TRT170227K64");

        List<EvdsDebtClient.EvdsDebtRow> rows = EvdsDebtClient.mergeHistoryRows(
                instrument,
                List.of(point(LocalDate.of(2026, 5, 23), "14.667")),
                List.of(
                        point(LocalDate.of(2026, 5, 22), "16.9"),
                        point(LocalDate.of(2026, 5, 23), "17.2")));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().asOf()).isEqualTo(LocalDate.of(2026, 5, 23).atStartOfDay());
    }

    @Test
    void mergeHistoryRows_appliesConfiguredScales() {
        EvdsProperties.Instrument instrument = instrument("TRT170227K64");
        instrument.setDirtyPriceScale(new BigDecimal("100"));
        instrument.setYieldScale(new BigDecimal("10"));

        List<EvdsDebtClient.EvdsDebtRow> rows = EvdsDebtClient.mergeHistoryRows(
                instrument,
                List.of(point(LocalDate.of(2026, 5, 23), "1466.7")),
                List.of(point(LocalDate.of(2026, 5, 23), "172")));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().dirtyPrice()).isEqualByComparingTo("14.667000");
        assertThat(rows.getFirst().couponRate()).isEqualByComparingTo("17.200000");
    }

    private static EvdsProperties.Instrument instrument(String isin) {
        EvdsProperties.Instrument instrument = new EvdsProperties.Instrument();
        instrument.setIsin(isin);
        instrument.setName(isin);
        instrument.setIssuer("Hazine");
        instrument.setMaturityDate("2027-02-17");
        instrument.setDirtyPriceScale(BigDecimal.ONE);
        instrument.setYieldScale(BigDecimal.ONE);
        return instrument;
    }

    private static EvdsSeriesPoint point(LocalDate day, String value) {
        return new EvdsSeriesPoint(day.atStartOfDay(), new BigDecimal(value));
    }
}
