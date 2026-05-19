package com.nurseli.marketdata.application.debt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EvdsCouponFrequencyHeuristicTest {

    @Test
    void semiAnnualCodes() {
        for (String code : new String[] {
            "24D2",
            "24T2",
            "24D2DK3151227",
            "61T2K3021030",
            "61T2DK10080131",
            "49T2DK8020130",
            "85T2DK3110832",
            "121T2K14050935"
        }) {
            var r = EvdsCouponFrequencyHeuristic.resolve(code);
            assertEquals(2, r.perYear(), code);
            assertEquals("6 ayda bir", r.label(), code);
            assertEquals(EvdsCouponFrequencyHeuristic.SOURCE, r.source(), code);
        }
    }

    @Test
    void quarterlyAndAnnual() {
        var q = EvdsCouponFrequencyHeuristic.resolve("XXD4YY");
        assertEquals(4, q.perYear());
        assertEquals("3 ayda bir", q.label());

        var a = EvdsCouponFrequencyHeuristic.resolve("ABT1C");
        assertEquals(1, a.perYear());
        assertEquals("Yılda bir", a.label());
    }

    @Test
    void unknownOrAmbiguous() {
        assertNull(EvdsCouponFrequencyHeuristic.resolve(null));
        assertNull(EvdsCouponFrequencyHeuristic.resolve(""));
        assertNull(EvdsCouponFrequencyHeuristic.resolve("TRT170227K64"));
        assertNull(EvdsCouponFrequencyHeuristic.resolve("TP_TRT170227K64"));
    }
}
