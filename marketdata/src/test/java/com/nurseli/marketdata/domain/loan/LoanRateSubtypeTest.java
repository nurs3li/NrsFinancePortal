package com.nurseli.marketdata.domain.loan;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanRateSubtypeTest {

    @Test
    void parseMany_emptyMeansAll() {
        Set<LoanRateSubtype> s = LoanRateSubtype.parseMany("");
        assertEquals(4, s.size());
    }

    @Test
    void parseMany_csvSubset() {
        Set<LoanRateSubtype> s = LoanRateSubtype.parseMany("CONSUMER_TRY, HOUSING_TRY");
        assertEquals(2, s.size());
        assertTrue(s.contains(LoanRateSubtype.CONSUMER_TRY));
        assertTrue(s.contains(LoanRateSubtype.HOUSING_TRY));
    }
}
