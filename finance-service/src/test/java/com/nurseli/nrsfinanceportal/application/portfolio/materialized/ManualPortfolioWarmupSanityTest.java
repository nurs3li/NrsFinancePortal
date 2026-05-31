package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManualPortfolioWarmupSanityTest {

    @Test
    void needsSanityFallback_detectsLargeDeviation() throws Exception {
        var method = ManualPortfolioWarmupService.class.getDeclaredMethod(
                "needsSanityFallback",
                List.class,
                com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView.class
        );
        method.setAccessible(true);

        var summary = new com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioSummaryView(
                1,
                1,
                0,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(90_000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(-10_000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(-10_000),
                BigDecimal.valueOf(-10),
                null,
                null,
                null,
                null
        );

        var aligned = List.of(new com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto(
                LocalDate.now(),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(92_000)
        ));
        var inflated = List.of(new com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto(
                LocalDate.now(),
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(1_800_000)
        ));

        assertThat(method.invoke(null, aligned, summary)).isEqualTo(false);
        assertThat(method.invoke(null, inflated, summary)).isEqualTo(true);
    }
}
