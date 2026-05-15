package com.nurseli.marketdata.application.deposit;

import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositRateCatalogTest {

    @Test
    void allSpecsCoverTryUsdEurAndGt1y() {
        var list = DepositRateCatalog.allSpecs();
        assertEquals(13, list.size());
        assertTrue(list.stream().anyMatch(s -> "TRY".equals(s.currency()) && "GT1Y".equals(s.term())));
        assertTrue(list.stream().anyMatch(s -> "USD".equals(s.currency()) && "1M".equals(s.term())));
        assertTrue(list.stream().anyMatch(s ->
                EvdsSeriesLogicalNames.DEPOSIT_RATE_EUR_1Y_WEEKLY.equals(s.evdsLogicalKey())));
    }
}
