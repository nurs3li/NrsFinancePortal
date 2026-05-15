package com.nurseli.marketdata.infrastructure.bist.isyatirim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import com.nurseli.marketdata.infrastructure.bist.BistProviderResult;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IsYatirimBistEquityProviderTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-14T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private final MockWebServer server = new MockWebServer();
    private BistProperties props;
    private IsYatirimBistEquityProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server.start();
        props = new BistProperties();
        props.setEnabled(true);
        props.setIsyatirimBaseUrl(server.url("/").toString().replaceAll("/$", ""));
        props.setDefaultLookbackYears(2);
        WebClient wc = WebClient.builder().baseUrl(props.getIsyatirimBaseUrl()).build();
        provider =
                new IsYatirimBistEquityProvider(
                        wc,
                        new BistSymbolCatalog(),
                        props,
                        new IsYatirimHisseTekilParser(FIXED_CLOCK),
                        new ObjectMapper(),
                        FIXED_CLOCK);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void whenDisabled_returnsDisabledWithoutHttp() throws Exception {
        props.setEnabled(false);
        server.enqueue(new MockResponse().setResponseCode(500));
        BistProviderResult<List<BistEquityDailyPrice>> r = provider.fetchHistory("ASELS", null, null);
        assertThat(r.success()).isFalse();
        assertThat(r.error()).isNotNull();
        assertThat(r.error().message()).contains("disabled");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void unsupportedSymbol_returnsFailure() {
        BistProviderResult<List<BistEquityDailyPrice>> r =
                provider.fetchHistory("NOTINTABLE", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        assertThat(r.success()).isFalse();
        assertThat(r.error().message()).contains("unsupported");
    }

    @Test
    void uriBuiltCorrectly() throws Exception {
        String body = "[]";
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(body));
        provider.fetchHistory("ASELS", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).contains("HisseTekil");
        assertThat(req.getRequestUrl().queryParameter("hisse")).isEqualTo("ASELS");
        assertThat(req.getRequestUrl().queryParameter("startdate")).isEqualTo("14-05-2024");
        assertThat(req.getRequestUrl().queryParameter("enddate")).isEqualTo("14-05-2026");
    }

    @Test
    void thyaoIs_normalizedToThyaoInQuery() throws Exception {
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody("[]"));
        provider.fetchHistory("THYAO.IS", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        RecordedRequest req = server.takeRequest();
        assertThat(req.getRequestUrl().queryParameter("hisse")).isEqualTo("THYAO");
    }

    @Test
    void sampleJson_returnsSuccess() throws Exception {
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
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(json));
        BistProviderResult<List<BistEquityDailyPrice>> r =
                provider.fetchHistory("ASELS", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        assertThat(r.success()).isTrue();
        assertThat(r.partial()).isFalse();
        assertThat(r.data()).hasSize(1);
        assertThat(r.data().get(0).symbol()).isEqualTo("ASELS");
    }

    @Test
    void valueEnvelope_returnsSuccess() throws Exception {
        String json =
                """
                {"ok":true,"errorCode":null,"errorDescription":null,"transactionId":"x","value":[{
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
                }]}
                """;
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(json));
        BistProviderResult<List<BistEquityDailyPrice>> r =
                provider.fetchHistory("ASELS", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        assertThat(r.success()).isTrue();
        assertThat(r.data()).hasSize(1);
        assertThat(r.data().get(0).symbol()).isEqualTo("ASELS");
    }

    @Test
    void parserSkipsRow_returnsPartial() throws Exception {
        String json =
                """
                [
                  {"HGDG_HS_KODU":"ASELS","HGDG_TARIH":"bad-date","HGDG_KAPANIS":1},
                  {
                    "HGDG_HS_KODU": "ASELS",
                    "HGDG_TARIH": "12-09-2024",
                    "HGDG_KAPANIS": 10,
                    "HGDG_AOF": 11,
                    "HGDG_MIN": 9,
                    "HGDG_MAX": 12,
                    "HGDG_HACIM": 100,
                    "END_ENDEKS_KODU": "01",
                    "END_TARIH": 1,
                    "END_SEANS": 1,
                    "END_DEGER": 1,
                    "DD_DOVIZ_KODU": "USD",
                    "DD_DT_KODU": "01",
                    "DD_TARIH": 1,
                    "DD_DEGER": 1,
                    "DOLAR_BAZLI_FIYAT": 1,
                    "ENDEKS_BAZLI_FIYAT": 1,
                    "DOLAR_HACIM": 1,
                    "SERMAYE": 1,
                    "HG_KAPANIS": 10,
                    "HG_AOF": 11,
                    "HG_MIN": 9,
                    "HG_MAX": 12,
                    "PD": 1,
                    "PD_USD": 1,
                    "HAO_PD": 1,
                    "HAO_PD_USD": 1,
                    "HG_HACIM": 100,
                    "DOLAR_BAZLI_MIN": 1,
                    "DOLAR_BAZLI_MAX": 1,
                    "DOLAR_BAZLI_AOF": 1
                  }
                ]
                """;
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(json));
        BistProviderResult<List<BistEquityDailyPrice>> r =
                provider.fetchHistory("ASELS", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        assertThat(r.success()).isTrue();
        assertThat(r.partial()).isTrue();
        assertThat(r.data()).hasSize(1);
        assertThat(r.warnings()).isNotEmpty();
    }

    @Test
    void emptyJsonArray_returnsEmptyResult() throws Exception {
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody("[]"));
        BistProviderResult<List<BistEquityDailyPrice>> r =
                provider.fetchHistory("ASELS", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        assertThat(r.success()).isTrue();
        assertThat(r.data()).isEmpty();
    }

    @Test
    void http500_returnsFailure_noException() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("err"));
        BistProviderResult<List<BistEquityDailyPrice>> r =
                provider.fetchHistory("ASELS", LocalDate.of(2024, 5, 14), LocalDate.of(2026, 5, 14));
        assertThat(r.success()).isFalse();
        assertThat(r.error()).isNotNull();
        assertThat(r.error().httpStatus()).isEqualTo(500);
    }

    @Test
    void invalidDateRange_returnsFailure() {
        BistProviderResult<List<BistEquityDailyPrice>> r =
                provider.fetchHistory(
                        "ASELS",
                        LocalDate.of(2026, 5, 14),
                        LocalDate.of(2024, 5, 14));
        assertThat(r.success()).isFalse();
        assertThat(r.error().message()).contains("invalid date range");
    }

    @Test
    void fetchLatest_picksMaxDate() throws Exception {
        String json =
                """
                [
                  {
                    "HGDG_HS_KODU": "GARAN",
                    "HGDG_TARIH": "10-09-2024",
                    "HGDG_KAPANIS": 10,
                    "HGDG_AOF": 11,
                    "HGDG_MIN": 9,
                    "HGDG_MAX": 12,
                    "HGDG_HACIM": 100,
                    "END_ENDEKS_KODU": "01",
                    "END_TARIH": 1,
                    "END_SEANS": 1,
                    "END_DEGER": 1,
                    "DD_DOVIZ_KODU": "USD",
                    "DD_DT_KODU": "01",
                    "DD_TARIH": 1,
                    "DD_DEGER": 1,
                    "DOLAR_BAZLI_FIYAT": 1,
                    "ENDEKS_BAZLI_FIYAT": 1,
                    "DOLAR_HACIM": 1,
                    "SERMAYE": 1,
                    "HG_KAPANIS": 10,
                    "HG_AOF": 11,
                    "HG_MIN": 9,
                    "HG_MAX": 12,
                    "PD": 1,
                    "PD_USD": 1,
                    "HAO_PD": 1,
                    "HAO_PD_USD": 1,
                    "HG_HACIM": 100,
                    "DOLAR_BAZLI_MIN": 1,
                    "DOLAR_BAZLI_MAX": 1,
                    "DOLAR_BAZLI_AOF": 1
                  },
                  {
                    "HGDG_HS_KODU": "GARAN",
                    "HGDG_TARIH": "15-09-2024",
                    "HGDG_KAPANIS": 20,
                    "HGDG_AOF": 21,
                    "HGDG_MIN": 19,
                    "HGDG_MAX": 22,
                    "HGDG_HACIM": 200,
                    "END_ENDEKS_KODU": "01",
                    "END_TARIH": 1,
                    "END_SEANS": 1,
                    "END_DEGER": 1,
                    "DD_DOVIZ_KODU": "USD",
                    "DD_DT_KODU": "01",
                    "DD_TARIH": 1,
                    "DD_DEGER": 1,
                    "DOLAR_BAZLI_FIYAT": 1,
                    "ENDEKS_BAZLI_FIYAT": 1,
                    "DOLAR_HACIM": 1,
                    "SERMAYE": 1,
                    "HG_KAPANIS": 20,
                    "HG_AOF": 21,
                    "HG_MIN": 19,
                    "HG_MAX": 22,
                    "PD": 1,
                    "PD_USD": 1,
                    "HAO_PD": 1,
                    "HAO_PD_USD": 1,
                    "HG_HACIM": 200,
                    "DOLAR_BAZLI_MIN": 1,
                    "DOLAR_BAZLI_MAX": 1,
                    "DOLAR_BAZLI_AOF": 1
                  }
                ]
                """;
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json").setBody(json));
        BistProviderResult<Optional<BistEquityDailyPrice>> latest = provider.fetchLatest("GARAN");
        assertThat(latest.success()).isTrue();
        assertThat(latest.data()).isPresent();
        assertThat(latest.data().get().date()).isEqualTo(LocalDate.of(2024, 9, 15));
        assertThat(latest.data().get().adjustedClose()).isEqualByComparingTo(new BigDecimal("20"));
    }
}
