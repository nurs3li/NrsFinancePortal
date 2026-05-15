package com.nurseli.marketdata.infrastructure.isyatirim.viop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.viop.domain.ViopDataQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class IsYatirimViopParsersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void historicalParserAcceptsIntegerLikePrice() throws Exception {
        var parser = new IsYatirimViopHistoricalParser(objectMapper);
        String json = "{\"data\":[[1778050800000,19966]],\"timestamp\":\"2026-05-13T20:46:55+03:00\"}";
        var parsed = parser.parse(json);
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().price()).isEqualByComparingTo(new BigDecimal("19966"));
    }

    @Test
    void historicalParserAcceptsDecimalPrice() throws Exception {
        var parser = new IsYatirimViopHistoricalParser(objectMapper);
        String json = "{\"data\":[[1778050800000,55.901]]}";
        var parsed = parser.parse(json);
        assertThat(parsed.rows().getFirst().price()).isEqualByComparingTo(new BigDecimal("55.901"));
    }

    @Test
    void snapshotParserComputesChangeFields() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"updateDate\":\"2026-05-13T18:01:42.000+03\",\"bid\":38.1,\"ask\":38.2,\"low\":38.0,\"high\":38.5,"
                        + "\"last\":38.3,\"dayClose\":38.0,\"open\":38.05,\"quantity\":100,\"volume\":1000,\"settlement\":38.15,"
                        + "\"preSettlement\":38.0,\"limitUp\":40,\"limitDown\":36,\"priceStep\":0.05,\"initialMargin\":5000,"
                        + "\"weekLow\":37.9,\"weekHigh\":38.6,\"weekClose\":38.1,\"monthLow\":37.5,\"monthHigh\":39.0,"
                        + "\"monthClose\":38.2,\"yearClose\":35.0,\"prevYearClose\":34.0,\"symbol\":\"F_USDTRY1226\"}]";
        var p = parser.parse(json, "F_USDTRY1226");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("38.3"));
        assertThat(p.changeAmount()).isEqualByComparingTo(new BigDecimal("0.3"));
        assertThat(p.changePercent()).isEqualByComparingTo(new BigDecimal("0.789474"));
    }

    @Test
    void snapshotParserParsesOnsGoldSample() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"symbol\":\"F_XAUUSD1026\",\"bid\":4841.2,\"ask\":4866.5,\"low\":4865.9,\"high\":4879,"
                        + "\"last\":4874.3,\"dayClose\":4875.8,\"quantity\":36,\"volume\":7944814.39,"
                        + "\"settlement\":4877.3,\"priceStep\":0.1,\"initialMargin\":487.73,"
                        + "\"updateDate\":\"2026-05-13T20:40:39.000+03\",\"open\":4870.3,"
                        + "\"monthHigh\":4972.1,\"monthLow\":4509.6,\"weekLow\":4834,\"weekHigh\":4972.1,"
                        + "\"weekClose\":4901,\"monthClose\":4760.1,\"yearClose\":3322.4,\"prevYearClose\":4425.2}]";
        var p = parser.parse(json, "F_XAUUSD1026");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.symbolFromProvider()).isEqualTo("F_XAUUSD1026");
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("4874.3"));
        assertThat(p.volume()).isEqualByComparingTo(new BigDecimal("7944814.39"));
        assertThat(p.initialMargin()).isEqualByComparingTo(new BigDecimal("487.73"));
        assertThat(p.priceStep()).isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    void snapshotParserParsesEquityFuturesSample() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"symbol\":\"F_GARAN0726\",\"bid\":142.1,\"ask\":147.85,\"low\":141.2,\"high\":147.1,"
                        + "\"last\":147.1,\"dayClose\":145.3,\"quantity\":38,\"volume\":541605,\"settlement\":142.2,"
                        + "\"priceStep\":0.05,\"initialMargin\":1962.36,\"updateDate\":\"2026-05-13T18:04:34.000+03\","
                        + "\"open\":143.4}]";
        var p = parser.parse(json, "F_GARAN0726");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("147.1"));
        assertThat(p.volume()).isEqualByComparingTo(new BigDecimal("541605"));
        assertThat(p.initialMargin()).isEqualByComparingTo(new BigDecimal("1962.36"));
        assertThat(p.priceStep()).isEqualByComparingTo(new BigDecimal("0.05"));
    }

    @Test
    void snapshotParserParsesThyaoEquitySample() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"symbol\":\"F_THYAO0726\",\"bid\":328,\"ask\":329,\"low\":329,\"high\":335,"
                        + "\"last\":329,\"dayClose\":330.5,\"quantity\":198,\"volume\":6580780,\"settlement\":329.85,"
                        + "\"priceStep\":0.05,\"preSettlement\":330.5,\"initialMargin\":4419.99,"
                        + "\"updateDate\":\"2026-05-13T18:01:28.000+03\",\"open\":333.15,"
                        + "\"monthHigh\":343.15,\"monthLow\":325,\"weekLow\":329,\"weekHigh\":342.05,"
                        + "\"weekClose\":337.75,\"monthClose\":294.35,\"yearClose\":331.75,\"prevYearClose\":272.8}]";
        var p = parser.parse(json, "F_THYAO0726");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("329"));
        assertThat(p.preSettlement()).isEqualByComparingTo(new BigDecimal("330.5"));
        assertThat(p.initialMargin()).isEqualByComparingTo(new BigDecimal("4419.99"));
    }

    @Test
    void snapshotParserParsesAselsEquitySample() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"symbol\":\"F_ASELS0726\",\"bid\":442.9,\"ask\":444.85,\"low\":441,\"high\":454,"
                        + "\"last\":444.55,\"dayClose\":455.1,\"quantity\":121,\"volume\":5412975,\"settlement\":443.8,"
                        + "\"priceStep\":0.05,\"preSettlement\":455.1,\"initialMargin\":7012.04,"
                        + "\"updateDate\":\"2026-05-13T17:43:32.000+03\",\"open\":444.45,"
                        + "\"monthHigh\":483.65,\"monthLow\":441,\"weekLow\":441,\"weekHigh\":469.9,"
                        + "\"weekClose\":470.25,\"monthClose\":183.6,\"yearClose\":119.15,\"prevYearClose\":183.6}]";
        var p = parser.parse(json, "F_ASELS0726");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("444.55"));
        assertThat(p.settlement()).isEqualByComparingTo(new BigDecimal("443.8"));
        assertThat(p.initialMargin()).isEqualByComparingTo(new BigDecimal("7012.04"));
    }

    @Test
    void snapshotParserParsesAkbnkEquitySample() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"symbol\":\"F_AKBNK0726\",\"bid\":77.25,\"ask\":77.58,\"low\":76.91,\"high\":79.39,"
                        + "\"last\":77.46,\"dayClose\":78.62,\"quantity\":168,\"volume\":1308523,\"settlement\":77.39,"
                        + "\"priceStep\":0.01,\"preSettlement\":78.62,\"initialMargin\":1114.416,"
                        + "\"updateDate\":\"2026-05-13T17:50:32.000+03\",\"open\":78.62,"
                        + "\"monthHigh\":82.88,\"monthLow\":76.38,\"weekLow\":76.91,\"weekHigh\":82.88,"
                        + "\"weekClose\":80.91,\"monthClose\":64.72,\"yearClose\":52.3,\"prevYearClose\":64.72}]";
        var p = parser.parse(json, "F_AKBNK0726");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("77.46"));
        assertThat(p.priceStep()).isEqualByComparingTo(new BigDecimal("0.01"));
        assertThat(p.initialMargin()).isEqualByComparingTo(new BigDecimal("1114.416"));
    }

    @Test
    void snapshotParserParsesSiseEquitySample() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"symbol\":\"F_SISE0726\",\"bid\":53.65,\"ask\":54.11,\"low\":53.2,\"high\":55,"
                        + "\"last\":54.11,\"dayClose\":54.59,\"quantity\":1389,\"volume\":7525809,\"settlement\":54.2,"
                        + "\"priceStep\":0.01,\"preSettlement\":54.59,\"initialMargin\":715.44,"
                        + "\"updateDate\":\"2026-05-13T18:06:42.000+03\",\"open\":54.59,"
                        + "\"monthHigh\":57.78,\"monthLow\":49,\"weekLow\":52.35,\"weekHigh\":57.78,"
                        + "\"weekClose\":53.4,\"monthClose\":37.33,\"yearClose\":38.58,\"prevYearClose\":37.33}]";
        var p = parser.parse(json, "F_SISE0726");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("54.11"));
        assertThat(p.volume()).isEqualByComparingTo(new BigDecimal("7525809"));
        assertThat(p.initialMargin()).isEqualByComparingTo(new BigDecimal("715.44"));
    }

    @Test
    void snapshotParserParsesEreglEquitySample() throws Exception {
        var parser = new IsYatirimViopSnapshotParser(objectMapper);
        String json =
                "[{\"symbol\":\"F_EREGL0726\",\"bid\":43.01,\"ask\":43.37,\"low\":41.9,\"high\":43.62,"
                        + "\"last\":43.02,\"dayClose\":43.17,\"quantity\":527,\"volume\":2249529,\"settlement\":43.46,"
                        + "\"priceStep\":0.01,\"preSettlement\":43.17,\"initialMargin\":599.748,"
                        + "\"updateDate\":\"2026-05-13T18:04:55.000+03\",\"open\":42.96,"
                        + "\"monthHigh\":46.73,\"monthLow\":36.91,\"weekLow\":40,\"weekHigh\":46.73,"
                        + "\"weekClose\":40.04,\"monthClose\":23.84,\"yearClose\":22.57,\"prevYearClose\":23.84}]";
        var p = parser.parse(json, "F_EREGL0726");
        assertThat(p.dataQuality()).isEqualTo(ViopDataQuality.OK);
        assertThat(p.last()).isEqualByComparingTo(new BigDecimal("43.02"));
        assertThat(p.settlement()).isEqualByComparingTo(new BigDecimal("43.46"));
        assertThat(p.initialMargin()).isEqualByComparingTo(new BigDecimal("599.748"));
    }

    @Test
    void historicalParserReturnsEmptyListWhenDataEmpty() throws Exception {
        var parser = new IsYatirimViopHistoricalParser(objectMapper);
        String json = "{\"data\":[],\"timestamp\":\"2026-05-14T20:31:37.4298408+03:00\"}";
        var parsed = parser.parse(json);
        assertThat(parsed.rows()).isEmpty();
    }

    @Test
    void historicalParserSkipsNullAndNonPositivePrices() throws Exception {
        var parser = new IsYatirimViopHistoricalParser(objectMapper);
        String json = "{\"data\":[[1715029200000,null],[1715115600000,0],[-1,100],[1715202000000,2309.00488]]}";
        var parsed = parser.parse(json);
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().millis()).isEqualTo(1715202000000L);
    }
}
