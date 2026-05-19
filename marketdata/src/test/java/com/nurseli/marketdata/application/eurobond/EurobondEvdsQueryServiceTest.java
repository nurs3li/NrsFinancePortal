package com.nurseli.marketdata.application.eurobond;

import com.nurseli.marketdata.api.dto.eurobond.EurobondLatestMetricsDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EurobondEvdsQueryServiceTest {

    @Test
    void sharePct_nullSafe() {
        var m = new EurobondLatestMetricsDto(
                new BigDecimal("1000"),
                new BigDecimal("900"),
                new BigDecimal("1000"),
                new BigDecimal("200"),
                new BigDecimal("800"),
                new BigDecimal("100"),
                new BigDecimal("900"),
                new BigDecimal("600"),
                new BigDecimal("300"),
                new BigDecimal("100"));
        var shares = EurobondEvdsQueryService.buildShares(m);
        assertEquals(new BigDecimal("60.0000"), shares.usdSharePct());
        assertEquals(new BigDecimal("30.0000"), shares.eurSharePct());
        assertEquals(new BigDecimal("10.0000"), shares.jpySharePct());
        assertEquals(new BigDecimal("80.0000"), shares.remainingLongSharePct());
        assertEquals(new BigDecimal("20.0000"), shares.remainingShortSharePct());
    }

    @Test
    void sharePct_zeroTotal() {
        var m = new EurobondLatestMetricsDto(null, null, BigDecimal.ZERO, null, null, null, null, null, null, null);
        var shares = EurobondEvdsQueryService.buildShares(m);
        assertNull(shares.usdSharePct());
    }
}
