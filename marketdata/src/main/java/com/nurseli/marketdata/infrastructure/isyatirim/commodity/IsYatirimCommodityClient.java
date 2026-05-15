package com.nurseli.marketdata.infrastructure.isyatirim.commodity;

import com.nurseli.marketdata.config.MarketMetalsIsyatirimProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class IsYatirimCommodityClient {

    private static final String ACCEPT = "application/json, text/javascript, */*; q=0.01";
    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private final MarketMetalsIsyatirimProperties properties;

    /**
     * Ham JSON gövdesi; parse {@link com.nurseli.marketdata.infrastructure.isyatirim.viop.IsYatirimViopHistoricalParser}.
     */
    public String fetchHistoricalChart(String providerSymbol, LocalDateTime from, LocalDateTime to, int periodMinutes) {
        long t0 = System.currentTimeMillis();
        log.info(
                "ISYATIRIM_METAL_FETCH_STARTED event=ISYATIRIM_METAL_FETCH_STARTED endeks={} from={} to={} period={}",
                providerSymbol,
                from,
                to,
                periodMinutes);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(IST);
        String fromStr = fmt.format(from.atZone(IST));
        String toStr = fmt.format(to.atZone(IST));

        WebClient client = buildClient();
        try {
            String body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getHistoricalPath())
                            .queryParam("period", periodMinutes)
                            .queryParam("from", fromStr)
                            .queryParam("to", toStr)
                            .queryParam("endeks", providerSymbol)
                            .build())
                    .header(HttpHeaders.ACCEPT, ACCEPT)
                    .header(HttpHeaders.REFERER, properties.getReferer())
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header(HttpHeaders.USER_AGENT, properties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + (long) properties.getConnectTimeoutMs()));
            log.info(
                    "ISYATIRIM_METAL_FETCH_SUCCEEDED event=ISYATIRIM_METAL_FETCH_SUCCEEDED endeks={} durationMs={}",
                    providerSymbol,
                    System.currentTimeMillis() - t0);
            return body;
        } catch (Exception e) {
            log.warn(
                    "ISYATIRIM_METAL_FETCH_FAILED event=ISYATIRIM_METAL_FETCH_FAILED endeks={} durationMs={} error={}",
                    providerSymbol,
                    System.currentTimeMillis() - t0,
                    e.getMessage());
            throw e;
        }
    }

    private WebClient buildClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        return WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
