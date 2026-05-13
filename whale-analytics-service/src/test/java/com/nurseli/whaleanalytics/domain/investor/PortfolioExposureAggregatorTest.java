package com.nurseli.whaleanalytics.domain.investor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioExposureAggregatorTest {

    @Test
    void aggregatesOpenAndClosedWithRatios() {
        List<InvestorPositionSnapshot> snaps = List.of(
                new InvestorPositionSnapshot(1L, 10L, "STOCK", "ASELS", BigDecimal.ONE, "OPEN",
                        new BigDecimal("100"), new BigDecimal("150"), null,
                        new BigDecimal("50"), null, Instant.now()),
                new InvestorPositionSnapshot(2L, 10L, "CRYPTO", "BTCUSDT", BigDecimal.ONE, "CLOSED",
                        new BigDecimal("200"), null, new BigDecimal("220"),
                        new BigDecimal("20"), null, Instant.now())
        );
        PortfolioExposureSummary s = PortfolioExposureAggregator.aggregate(snaps);
        assertThat(s.totalPortfolioValueTry()).isEqualByComparingTo(new BigDecimal("370"));
        assertThat(s.positionCount()).isEqualTo(2);
        assertThat(s.openPositionCount()).isEqualTo(1);
        assertThat(s.closedPositionCount()).isEqualTo(1);
        assertThat(s.largestPositionSymbol()).isEqualTo("BTCUSDT");
        BigDecimal expectedRatio = new BigDecimal("220").divide(new BigDecimal("370"), 6, java.math.RoundingMode.HALF_UP);
        assertThat(s.largestPositionRatio()).isEqualByComparingTo(expectedRatio);
        assertThat(s.cryptoExposureRatio().signum()).isGreaterThan(0);
    }

    @Test
    void emptySnapshotsYieldZero() {
        PortfolioExposureSummary s = PortfolioExposureAggregator.aggregate(List.of());
        assertThat(s.totalPortfolioValueTry()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
