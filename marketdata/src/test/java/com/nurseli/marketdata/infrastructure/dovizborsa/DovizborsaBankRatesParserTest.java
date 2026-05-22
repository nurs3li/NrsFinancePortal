package com.nurseli.marketdata.infrastructure.dovizborsa;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DovizborsaBankRatesParserTest {

    private final DovizborsaBankRatesParser parser = new DovizborsaBankRatesParser();

    @Test
    void parseSnippet_extractsUsdAndEurWithSuperscriptPrices() throws IOException {
        String html = new String(
                getClass().getResourceAsStream("/dovizborsa-banka-snippet.html").readAllBytes(),
                StandardCharsets.UTF_8);
        List<DovizborsaParsedRate> rows = parser.parse(html);
        assertEquals(3, rows.size());

        DovizborsaParsedRate deniz = rows.stream().filter(r -> "DNZUSD".equals(r.bankCode())).findFirst().orElseThrow();
        assertEquals("USD", deniz.currency());
        assertEquals(new BigDecimal("44.9102"), deniz.buy());
        assertEquals(new BigDecimal("46.2583"), deniz.sell());
        assertEquals("UP", deniz.trend());

        DovizborsaParsedRate garantiUsd = rows.stream().filter(r -> "GARUSD".equals(r.bankCode())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("38.7910"), garantiUsd.buy());
        assertEquals("DOWN", garantiUsd.trend());

        DovizborsaParsedRate garantiEur = rows.stream().filter(r -> "GAREUR".equals(r.bankCode())).findFirst().orElseThrow();
        assertEquals("EUR", garantiEur.currency());
        assertTrue(garantiEur.sell().compareTo(new BigDecimal("47")) > 0);
    }

    @Test
    void parsePriceSpan_handlesCommaAndSup() {
        assertEquals(new BigDecimal("38.7910"), DovizborsaBankRatesParser.parsePriceSpan(
                org.jsoup.Jsoup.parse("<span>38,79<sup>10</sup></span>").selectFirst("span")));
    }
}
