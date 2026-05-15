package com.nurseli.marketdata.infrastructure.bist.isyatirim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.infrastructure.bist.BistDataQuality;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import com.nurseli.marketdata.infrastructure.bist.BistParseResult;
import com.nurseli.marketdata.infrastructure.bist.BistProviderSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IsYatirimHisseTekilParserTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-14T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private final IsYatirimHisseTekilParser parser = new IsYatirimHisseTekilParser(FIXED_CLOCK);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sampleAselsRow_mapsAllFields_andHistorical() throws Exception {
        String json =
                """
                [{
                  "HGDG_HS_KODU": "ASELS",
                  "HGDG_TARIH": "11-09-2024",
                  "HGDG_KAPANIS": 54.7406,
                  "HGDG_AOF": 55.83842,
                  "HGDG_MIN": 54.59105,
                  "HGDG_MAX": 57.03394,
                  "HGDG_HACIM": 2097001074.000000,
                  "END_ENDEKS_KODU": "01",
                  "END_TARIH": 1726002000000,
                  "END_SEANS": 2,
                  "END_DEGER": 9419.66,
                  "DD_DOVIZ_KODU": "USD",
                  "DD_DT_KODU": "01",
                  "DD_TARIH": 1726002000000,
                  "DD_DEGER": 34.001,
                  "DOLAR_BAZLI_FIYAT": 1.6100,
                  "ENDEKS_BAZLI_FIYAT": 0.0058,
                  "DOLAR_HACIM": 61674688.2151,
                  "SERMAYE": 4.56E9,
                  "HG_KAPANIS": 54.9,
                  "HG_AOF": 56.001,
                  "HG_MIN": 54.75,
                  "HG_MAX": 57.2,
                  "PD": 250344006958.00781,
                  "PD_USD": 7362842473.98629,
                  "HAO_PD": 64538684993.774413,
                  "HAO_PD_USD": 1898140789.793666,
                  "HG_HACIM": 2.097001074E9,
                  "DOLAR_BAZLI_MIN": 1.6056,
                  "DOLAR_BAZLI_MAX": 1.6774,
                  "DOLAR_BAZLI_AOF": 1.6423
                }]
                """;
        List<IsYatirimHisseTekilRow> rows = objectMapper.readValue(json, new TypeReference<>() {});
        BistParseResult result = parser.parse(rows);

        assertThat(result.skippedCount()).isZero();
        assertThat(result.rows()).hasSize(1);
        BistEquityDailyPrice p = result.rows().get(0);
        assertThat(p.symbol()).isEqualTo("ASELS");
        assertThat(p.date()).isEqualTo(LocalDate.of(2024, 9, 11));
        assertThat(p.adjustedClose()).isEqualByComparingTo("54.7406");
        assertThat(p.adjustedAverage()).isEqualByComparingTo("55.83842");
        assertThat(p.adjustedLow()).isEqualByComparingTo("54.59105");
        assertThat(p.adjustedHigh()).isEqualByComparingTo("57.03394");
        assertThat(p.adjustedVolume()).isEqualByComparingTo("2097001074");
        assertThat(p.rawClose()).isEqualByComparingTo("54.9");
        assertThat(p.rawAverage()).isEqualByComparingTo("56.001");
        assertThat(p.rawLow()).isEqualByComparingTo("54.75");
        assertThat(p.rawHigh()).isEqualByComparingTo("57.2");
        assertThat(p.rawVolume()).isEqualByComparingTo(new BigDecimal("2.097001074E9"));
        assertThat(p.usdTry()).isEqualByComparingTo("34.001");
        assertThat(p.bist100Value()).isEqualByComparingTo("9419.66");
        assertThat(p.usdPrice()).isEqualByComparingTo("1.6100");
        assertThat(p.indexBasedPrice()).isEqualByComparingTo("0.0058");
        assertThat(p.usdVolume()).isEqualByComparingTo("61674688.2151");
        assertThat(p.capital()).isEqualByComparingTo(new BigDecimal("4.56E9"));
        assertThat(p.marketCapTry()).isEqualByComparingTo("250344006958.00781");
        assertThat(p.marketCapUsd()).isEqualByComparingTo("7362842473.98629");
        assertThat(p.freeFloatMarketCapTry()).isEqualByComparingTo("64538684993.774413");
        assertThat(p.freeFloatMarketCapUsd()).isEqualByComparingTo("1898140789.793666");
        assertThat(p.dollarBasedLow()).isEqualByComparingTo("1.6056");
        assertThat(p.dollarBasedHigh()).isEqualByComparingTo("1.6774");
        assertThat(p.dollarBasedAverage()).isEqualByComparingTo("1.6423");
        assertThat(p.source()).isEqualTo(BistProviderSource.IS_YATIRIM);
        assertThat(p.dataQuality()).isEqualTo(BistDataQuality.HISTORICAL);
        assertThat(p.lastUpdated()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void invalidDateRow_skipped() {
        IsYatirimHisseTekilRow bad = fullHistorical("X", "not-a-date", BigDecimal.valueOf(50));
        IsYatirimHisseTekilRow good = fullHistorical("X", "12-09-2024", BigDecimal.valueOf(50));
        BistParseResult r = parser.parse(List.of(bad, good));
        assertThat(r.skippedCount()).isEqualTo(1);
        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).date()).isEqualTo(LocalDate.of(2024, 9, 12));
        assertThat(r.warnings()).anyMatch(w -> w.contains("invalid HGDG_TARIH"));
    }

    @Test
    void missingHgdgKapanis_skipped() {
        IsYatirimHisseTekilRow row = fullHistorical("GARAN", "10-09-2024", null);
        BistParseResult r = parser.parse(List.of(row));
        assertThat(r.rows()).isEmpty();
        assertThat(r.skippedCount()).isEqualTo(1);
        assertThat(r.warnings()).anyMatch(w -> w.contains("missing HGDG_KAPANIS"));
    }

    @Test
    void optionalEnrichmentNull_rowPresent_partialQuality() {
        IsYatirimHisseTekilRow row =
                new IsYatirimHisseTekilRow(
                        "THYAO",
                        "01-01-2024",
                        new BigDecimal("10.5"),
                        new BigDecimal("10.6"),
                        new BigDecimal("10.4"),
                        new BigDecimal("10.7"),
                        new BigDecimal("1000"),
                        "01",
                        1L,
                        1,
                        null,
                        "USD",
                        "01",
                        1L,
                        null,
                        new BigDecimal("1.1"),
                        new BigDecimal("0.01"),
                        new BigDecimal("100"),
                        new BigDecimal("1e9"),
                        new BigDecimal("10.5"),
                        new BigDecimal("10.6"),
                        new BigDecimal("10.4"),
                        new BigDecimal("10.7"),
                        new BigDecimal("1000"),
                        new BigDecimal("1e12"),
                        new BigDecimal("2e10"),
                        new BigDecimal("1e11"),
                        new BigDecimal("2e9"),
                        new BigDecimal("1.0"),
                        new BigDecimal("1.1"),
                        new BigDecimal("1.05"));
        BistParseResult r = parser.parse(List.of(row));
        assertThat(r.rows()).hasSize(1);
        assertThat(r.rows().get(0).dataQuality()).isEqualTo(BistDataQuality.PARTIAL);
        assertThat(r.rows().get(0).usdTry()).isNull();
        assertThat(r.rows().get(0).bist100Value()).isNull();
    }

    @Test
    void rowsSortedByDateAscending() {
        IsYatirimHisseTekilRow later = fullHistorical("ASELS", "15-09-2024", new BigDecimal("60"));
        IsYatirimHisseTekilRow earlier = fullHistorical("ASELS", "10-09-2024", new BigDecimal("55"));
        BistParseResult r = parser.parse(List.of(later, earlier));
        assertThat(r.rows()).hasSize(2);
        assertThat(r.rows().get(0).date()).isEqualTo(LocalDate.of(2024, 9, 10));
        assertThat(r.rows().get(1).date()).isEqualTo(LocalDate.of(2024, 9, 15));
    }

    @Test
    void emptyInput_returnsEmptyResult() {
        assertThat(parser.parse(null)).isEqualTo(BistParseResult.empty());
        assertThat(parser.parse(List.of())).isEqualTo(BistParseResult.empty());
    }

    /**
     * {@link BistDataQuality#HISTORICAL} için tüm HGDG/HG ve zenginleştirme alanları dolu minimal satır.
     */
    private static IsYatirimHisseTekilRow fullHistorical(String symbol, String tarih, BigDecimal hgdgKapanis) {
        BigDecimal x = BigDecimal.valueOf(100);
        return new IsYatirimHisseTekilRow(
                symbol,
                tarih,
                hgdgKapanis,
                x,
                x,
                x,
                x,
                "01",
                1L,
                1,
                x,
                "USD",
                "01",
                1L,
                x,
                x,
                x,
                x,
                x,
                hgdgKapanis != null ? hgdgKapanis : x,
                x,
                x,
                x,
                x,
                x,
                x,
                x,
                x,
                x,
                x,
                x);
    }
}
