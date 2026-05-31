package com.nurseli.nrsfinanceportal.application.portfolio.materialized;

import com.nurseli.nrsfinanceportal.api.dto.ManualPortfolioTimeseriesPointDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManualPortfolioTimeseriesIncrementalServiceTest {

    private final ManualPortfolioTimeseriesIncrementalService service = new ManualPortfolioTimeseriesIncrementalService();

    @Test
    void mergeAdd_prefixUnchanged_tailSummed() {
        LocalDate d1 = LocalDate.of(2024, 1, 1);
        LocalDate d2 = LocalDate.of(2024, 6, 1);
        LocalDate mergeFrom = LocalDate.of(2024, 5, 1);

        List<ManualPortfolioTimeseriesPointDto> previous = List.of(
                new ManualPortfolioTimeseriesPointDto(d1, new BigDecimal("100"), new BigDecimal("110")),
                new ManualPortfolioTimeseriesPointDto(d2, new BigDecimal("200"), new BigDecimal("220"))
        );
        List<ManualPortfolioTimeseriesPointDto> delta = List.of(
                new ManualPortfolioTimeseriesPointDto(d1, new BigDecimal("50"), new BigDecimal("55")),
                new ManualPortfolioTimeseriesPointDto(d2, new BigDecimal("30"), new BigDecimal("33"))
        );

        List<ManualPortfolioTimeseriesPointDto> merged = service.mergeAdd(previous, delta, mergeFrom);

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).date()).isEqualTo(d1);
        assertThat(merged.get(0).openCostBasisTry()).isEqualByComparingTo("100");
        assertThat(merged.get(0).marketValueTry()).isEqualByComparingTo("110");

        assertThat(merged.get(1).date()).isEqualTo(d2);
        assertThat(merged.get(1).openCostBasisTry()).isEqualByComparingTo("230");
        assertThat(merged.get(1).marketValueTry()).isEqualByComparingTo("253");
    }

    @Test
    void computeMergeFrom_respectsOneYearStart() {
        LocalDate oneYearStart = LocalDate.of(2024, 3, 1);
        assertThat(service.computeMergeFrom(oneYearStart, buyDatePosition(LocalDate.of(2024, 8, 1))))
                .isEqualTo(LocalDate.of(2024, 7, 18));
        assertThat(service.computeMergeFrom(oneYearStart, buyDatePosition(LocalDate.of(2024, 1, 15))))
                .isEqualTo(oneYearStart);
    }

    private static com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition buyDatePosition(
            LocalDate buyDate
    ) {
        return com.nurseli.nrsfinanceportal.domain.portfolio.ManualPortfolioPosition.createNew(
                null,
                com.nurseli.nrsfinanceportal.domain.asset.AssetType.BIST,
                "TEST",
                BigDecimal.ONE,
                buyDate,
                BigDecimal.TEN,
                com.nurseli.nrsfinanceportal.domain.portfolio.ManualPriceSource.USER_INPUT,
                buyDate,
                true,
                BigDecimal.ZERO,
                com.nurseli.nrsfinanceportal.domain.portfolio.ManualPositionStatus.OPEN,
                null,
                null,
                null,
                null,
                false,
                null,
                null
        );
    }
}
