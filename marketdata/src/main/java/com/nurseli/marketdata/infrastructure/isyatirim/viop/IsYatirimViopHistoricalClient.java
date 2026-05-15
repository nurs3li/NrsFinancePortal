package com.nurseli.marketdata.infrastructure.isyatirim.viop;

import com.nurseli.marketdata.config.MarketViopProperties;
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
public class IsYatirimViopHistoricalClient {

    private static final String ACCEPT = "application/json, text/javascript, */*; q=0.01";

    private final MarketViopProperties viopProperties;

    public String fetchHistorical(String contractCode, LocalDateTime from, LocalDateTime to, int periodMinutes) {
        long t0 = System.currentTimeMillis();
        log.info(
                "VIOP_PROVIDER_HISTORY_REQUEST contractCode={} from={} to={} period={}",
                contractCode,
                from,
                to,
                periodMinutes);
        MarketViopProperties.IsYatirim cfg = viopProperties.getIsyatirim();
        ZoneId zone = ZoneId.of(viopProperties.getTimezone());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(zone);
        String fromStr = fmt.format(from.atZone(zone));
        String toStr = fmt.format(to.atZone(zone));

        WebClient client = buildClient(cfg);
        try {
            String body = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(cfg.getHistoryPath())
                            .queryParam("period", periodMinutes)
                            .queryParam("from", fromStr)
                            .queryParam("to", toStr)
                            .queryParam("endeks", contractCode)
                            .build())
                    .header(HttpHeaders.ACCEPT, ACCEPT)
                    .header(HttpHeaders.REFERER, cfg.getReferer())
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header(HttpHeaders.USER_AGENT, cfg.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofMillis(cfg.getReadTimeoutMs() + (long) cfg.getConnectTimeoutMs()));
            log.info(
                    "VIOP_PROVIDER_HISTORY_SUCCESS contractCode={} from={} to={} period={} durationMs={} dataQuality=OK",
                    contractCode,
                    from,
                    to,
                    periodMinutes,
                    System.currentTimeMillis() - t0);
            return body;
        } catch (Exception e) {
            log.warn(
                    "VIOP_PROVIDER_HISTORY_ERROR contractCode={} from={} to={} period={} durationMs={} dataQuality=PROVIDER_ERROR error={}",
                    contractCode,
                    from,
                    to,
                    periodMinutes,
                    System.currentTimeMillis() - t0,
                    e.getMessage());
            throw e;
        }
    }

    private static WebClient buildClient(MarketViopProperties.IsYatirim cfg) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));
        return WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
