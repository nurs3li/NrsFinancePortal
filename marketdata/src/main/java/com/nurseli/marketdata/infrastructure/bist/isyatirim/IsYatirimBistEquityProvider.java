package com.nurseli.marketdata.infrastructure.bist.isyatirim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.BistProperties;
import com.nurseli.marketdata.infrastructure.bist.BistEquityDailyPrice;
import com.nurseli.marketdata.infrastructure.bist.BistEquityMarketProvider;
import com.nurseli.marketdata.infrastructure.bist.BistParseResult;
import com.nurseli.marketdata.infrastructure.bist.BistProviderError;
import com.nurseli.marketdata.infrastructure.bist.BistProviderResult;
import com.nurseli.marketdata.infrastructure.bist.BistProviderSource;
import com.nurseli.marketdata.infrastructure.bist.BistSymbolCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * İş Yatırım HisseTekil JSON günlük serisi. Bean adı mevcut
 * {@code com.nurseli.marketdata.infrastructure.isyatirim.bist.IsYatirimBistEquityProvider} ile çakışmasın diye özelleştirildi.
 */
@Component("bistIsYatirimHisseTekilHttpProvider")
@Slf4j
public class IsYatirimBistEquityProvider implements BistEquityMarketProvider {

    public static final String SOURCE_NAME = "IS_YATIRIM";

    private static final String ACCEPT = "application/json, text/javascript, */*; q=0.01";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.isyatirim.com.tr/";
    private static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String HISSE_TEKIL_PATH = "/_layouts/15/Isyatirim.Website/Common/Data.aspx/HisseTekil";

    private final WebClient webClient;
    private final BistSymbolCatalog symbolCatalog;
    private final BistProperties bistProperties;
    private final IsYatirimHisseTekilParser parser;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public IsYatirimBistEquityProvider(
            WebClient.Builder webClientBuilder,
            BistSymbolCatalog symbolCatalog,
            BistProperties bistProperties,
            IsYatirimHisseTekilParser parser,
            ObjectMapper objectMapper,
            @Autowired(required = false) Clock clock) {
        this(
                buildWebClient(webClientBuilder, bistProperties),
                symbolCatalog,
                bistProperties,
                parser,
                objectMapper,
                clock != null ? clock : Clock.systemUTC());
    }

    /** Aynı paket testleri için doğrudan {@link WebClient} verilebilir. */
    IsYatirimBistEquityProvider(
            WebClient webClient,
            BistSymbolCatalog symbolCatalog,
            BistProperties bistProperties,
            IsYatirimHisseTekilParser parser,
            ObjectMapper objectMapper,
            Clock clock) {
        this.webClient = webClient;
        this.symbolCatalog = symbolCatalog;
        this.bistProperties = bistProperties;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private static WebClient buildWebClient(WebClient.Builder webClientBuilder, BistProperties bistProperties) {
        HttpClient httpClient =
                HttpClient.create().responseTimeout(Duration.ofMillis(Math.max(1, bistProperties.getReadTimeoutMs())));
        String base = bistProperties.getIsyatirimBaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://www.isyatirim.com.tr";
        }
        return webClientBuilder
                .baseUrl(trimTrailingSlash(base))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, ACCEPT)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.REFERER, REFERER)
                .build();
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * İş Yatırım HisseTekil bazen kökte dizi, bazen {@code {"ok":true,"value":[...]}} veya eski {@code {"d":[...]}}
     * sarmalayıcısı döner.
     */
    private List<IsYatirimHisseTekilRow> parseHisseTekilJsonBody(String body) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || root.isNull()) {
            return List.of();
        }
        if (root.isArray()) {
            return objectMapper.convertValue(root, new TypeReference<List<IsYatirimHisseTekilRow>>() {});
        }
        if (!root.isObject()) {
            throw new JsonProcessingException("HisseTekil: beklenmeyen JSON kökü (ne dizi ne nesne)") {};
        }
        if (root.has("ok") && root.get("ok").isBoolean() && !root.get("ok").asBoolean()) {
            String err =
                    root.has("errorDescription") && !root.get("errorDescription").isNull()
                            ? root.get("errorDescription").asText()
                            : "ok=false";
            throw new JsonProcessingException("HisseTekil: " + err) {};
        }
        for (String key : List.of("value", "d", "data", "result")) {
            JsonNode arr = root.get(key);
            if (arr != null && arr.isArray()) {
                return objectMapper.convertValue(arr, new TypeReference<List<IsYatirimHisseTekilRow>>() {});
            }
        }
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getValue().isArray()) {
                return objectMapper.convertValue(e.getValue(), new TypeReference<List<IsYatirimHisseTekilRow>>() {});
            }
        }
        throw new JsonProcessingException("HisseTekil: nesne içinde satır dizisi bulunamadı") {};
    }

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public boolean supports(String symbol) {
        if (symbol == null) {
            return false;
        }
        return symbolCatalog.isSupported(symbol.trim());
    }

    @Override
    public BistProviderResult<List<BistEquityDailyPrice>> fetchHistory(String symbol, LocalDate from, LocalDate to) {
        if (!bistProperties.isEnabled()) {
            log.debug("[BIST_ISY][HISTORY] disabled skip");
            return BistProviderResult.disabled("BIST provider is disabled");
        }
        String symRaw = symbol == null ? "" : symbol.trim();
        if (symRaw.isEmpty()) {
            return failure(symRaw, "symbol is blank", null, null);
        }
        if (!symbolCatalog.isSupported(symRaw)) {
            return failure(symRaw, "unsupported symbol: " + symRaw, null, null);
        }
        String isyatirimSymbol;
        try {
            isyatirimSymbol = symbolCatalog.toIsYatirimSymbol(symRaw);
        } catch (IllegalArgumentException ex) {
            return failure(symRaw, ex.getMessage(), null, ex);
        }

        LocalDate f = from;
        LocalDate t = to;
        LocalDate today = LocalDate.now(clock);
        if (f == null) {
            f = today.minusYears(Math.max(1, bistProperties.getDefaultLookbackYears()));
        }
        if (t == null) {
            t = today;
        }
        if (f.isAfter(t)) {
            return failure(isyatirimSymbol, "invalid date range: from is after to", null, null);
        }

        String startStr = f.format(QUERY_DATE);
        String endStr = t.format(QUERY_DATE);

        log.info(
                "[BIST_ISY][HISTORY] fetch started source={} symbol={} hisse={} from={} to={}",
                SOURCE_NAME,
                symRaw,
                isyatirimSymbol,
                startStr,
                endStr);

        Instant occurredAt = clock.instant();
        long t0 = System.currentTimeMillis();
        try {
            String body =
                    webClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path(HISSE_TEKIL_PATH)
                                                    .queryParam("hisse", isyatirimSymbol)
                                                    .queryParam("startdate", startStr)
                                                    .queryParam("enddate", endStr)
                                                    .build())
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(
                                    Duration.ofMillis(
                                            (long) bistProperties.getReadTimeoutMs()
                                                    + Math.max(1L, bistProperties.getConnectTimeoutMs())))
                            .block();

            if (body == null || body.isBlank()) {
                log.warn("[BIST_ISY][HISTORY] fetch failed source={} symbol={} reason=empty_body", SOURCE_NAME, symRaw);
                return failure(
                        isyatirimSymbol,
                        "empty response body",
                        null,
                        null,
                        occurredAt);
            }

            List<IsYatirimHisseTekilRow> rawRows = parseHisseTekilJsonBody(body);
            BistParseResult parse = parser.parse(rawRows);

            return toHistoryProviderResult(symRaw, isyatirimSymbol, parse, System.currentTimeMillis() - t0);
        } catch (WebClientResponseException ex) {
            log.warn(
                    "[BIST_ISY][HISTORY] fetch failed source={} symbol={} status={} durationMs={}",
                    SOURCE_NAME,
                    symRaw,
                    ex.getStatusCode().value(),
                    System.currentTimeMillis() - t0);
            return failure(isyatirimSymbol, ex.getMessage(), ex.getStatusCode().value(), ex, occurredAt);
        } catch (WebClientRequestException | JsonProcessingException ex) {
            log.warn(
                    "[BIST_ISY][HISTORY] fetch failed source={} symbol={} durationMs={} error={}",
                    SOURCE_NAME,
                    symRaw,
                    System.currentTimeMillis() - t0,
                    ex.getMessage());
            return failure(isyatirimSymbol, ex.getMessage(), null, ex, occurredAt);
        } catch (Exception ex) {
            log.warn(
                    "[BIST_ISY][HISTORY] fetch failed source={} symbol={} durationMs={} error={}",
                    SOURCE_NAME,
                    symRaw,
                    System.currentTimeMillis() - t0,
                    ex.getMessage());
            return failure(isyatirimSymbol, ex.getMessage(), null, ex, occurredAt);
        }
    }

    private BistProviderResult<List<BistEquityDailyPrice>> toHistoryProviderResult(
            String displaySymbol, String isyatirimSymbol, BistParseResult parse, long durationMs) {
        List<BistEquityDailyPrice> rows = parse.rows();
        int skipped = parse.skippedCount();
        List<String> pw = parse.warnings();

        if (rows.isEmpty() && skipped == 0 && pw.isEmpty()) {
            log.info(
                    "[BIST_ISY][HISTORY] fetch success source={} symbol={} rowCount=0 durationMs={}",
                    SOURCE_NAME,
                    displaySymbol,
                    durationMs);
            return BistProviderResult.empty("no rows after parse");
        }
        if (rows.isEmpty() && (skipped > 0 || !pw.isEmpty())) {
            log.info(
                    "[BIST_ISY][HISTORY] fetch partial source={} symbol={} rowCount=0 skippedCount={} warningCount={} durationMs={}",
                    SOURCE_NAME,
                    displaySymbol,
                    skipped,
                    pw.size(),
                    durationMs);
            return BistProviderResult.partial(List.of(), mergeWarnings(skipped, pw));
        }
        if (skipped > 0 || !pw.isEmpty()) {
            log.info(
                    "[BIST_ISY][HISTORY] fetch partial source={} symbol={} rowCount={} skippedCount={} warningCount={} durationMs={}",
                    SOURCE_NAME,
                    displaySymbol,
                    rows.size(),
                    skipped,
                    pw.size(),
                    durationMs);
            return BistProviderResult.partial(rows, mergeWarnings(skipped, pw));
        }
        log.info(
                "[BIST_ISY][HISTORY] fetch success source={} symbol={} rowCount={} durationMs={}",
                SOURCE_NAME,
                displaySymbol,
                rows.size(),
                durationMs);
        return BistProviderResult.success(rows);
    }

    private static List<String> mergeWarnings(int skipped, List<String> parseWarnings) {
        List<String> out = new ArrayList<>();
        if (skipped > 0) {
            out.add("skippedCount=" + skipped);
        }
        if (parseWarnings != null) {
            out.addAll(parseWarnings);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    @Override
    public BistProviderResult<Optional<BistEquityDailyPrice>> fetchLatest(String symbol) {
        if (!bistProperties.isEnabled()) {
            return BistProviderResult.disabled("BIST provider is disabled");
        }
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(14);
        BistProviderResult<List<BistEquityDailyPrice>> hist = fetchHistory(symbol, from, to);
        if (!hist.success()) {
            return BistProviderResult.failure(Objects.requireNonNull(hist.error()));
        }
        List<BistEquityDailyPrice> rows = hist.data() != null ? hist.data() : List.of();
        if (rows.isEmpty()) {
            if (hist.partial()) {
                return BistProviderResult.partial(Optional.empty(), BistProviderResult.copyWarnings(hist.warnings()));
            }
            return BistProviderResult.success(Optional.empty());
        }
        BistEquityDailyPrice latest =
                rows.stream().max(Comparator.comparing(BistEquityDailyPrice::date)).orElseThrow();
        if (hist.partial()) {
            return BistProviderResult.partial(Optional.of(latest), BistProviderResult.copyWarnings(hist.warnings()));
        }
        return BistProviderResult.success(Optional.of(latest));
    }

    private BistProviderResult<List<BistEquityDailyPrice>> failure(
            String symbol, String message, Integer httpStatus, Exception ex) {
        return failure(symbol, message, httpStatus, ex, clock.instant());
    }

    private BistProviderResult<List<BistEquityDailyPrice>> failure(
            String symbol, String message, Integer httpStatus, Exception ex, Instant occurredAt) {
        String exClass = ex == null ? null : ex.getClass().getName();
        BistProviderError err =
                BistProviderError.of(
                        BistProviderSource.IS_YATIRIM,
                        symbol == null ? "" : symbol.toUpperCase(Locale.ROOT),
                        message,
                        exClass,
                        httpStatus,
                        occurredAt);
        return BistProviderResult.failure(err);
    }
}
