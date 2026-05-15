package com.nurseli.marketdata.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EvdsPropertiesSeriesCodeTest {

    @Test
    void getSeriesCode_prefersPrimaryLogicalKey() {
        EvdsProperties p = new EvdsProperties();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("DEPOSIT_RATE_USD_1M_WEEKLY", "TP_USD_MT01");
        m.put("MARKET_MACRO_DEPOSIT_RATE_USD_1M_WEEKLY_CODE", "TP_ALT");
        p.setSeries(m);
        assertEquals("TP_USD_MT01", p.getSeriesCode("DEPOSIT_RATE_USD_1M_WEEKLY"));
    }

    @Test
    void getSeriesCode_fallsBackToMarketMacroSuffixCode() {
        EvdsProperties p = new EvdsProperties();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("MARKET_MACRO_DEPOSIT_RATE_USD_1M_WEEKLY_CODE", "TP_USD_MT01");
        p.setSeries(m);
        assertEquals("TP_USD_MT01", p.getSeriesCode("DEPOSIT_RATE_USD_1M_WEEKLY"));
    }

    @Test
    void getSeriesCode_blankPrimaryUsesMarketMacro() {
        EvdsProperties p = new EvdsProperties();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("DEPOSIT_RATE_USD_1M_WEEKLY", "   ");
        m.put("MARKET_MACRO_DEPOSIT_RATE_USD_1M_WEEKLY_CODE", "TP_USD_MT01");
        p.setSeries(m);
        assertEquals("TP_USD_MT01", p.getSeriesCode("DEPOSIT_RATE_USD_1M_WEEKLY"));
    }

    @Test
    void getSeriesCode_returnsNullWhenUnset() {
        EvdsProperties p = new EvdsProperties();
        p.setSeries(new LinkedHashMap<>());
        assertNull(p.getSeriesCode("DEPOSIT_RATE_USD_1M_WEEKLY"));
    }
}
