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

@Component
@RequiredArgsConstructor
@Slf4j
public class IsYatirimViopSnapshotClient {

    private static final String ACCEPT = "application/json, text/javascript, */*; q=0.01";

    private final MarketViopProperties viopProperties;

    public String fetchSnapshot(String contractCode) {
        long t0 = System.currentTimeMillis();
        log.info("VIOP_PROVIDER_SNAPSHOT_REQUEST contractCode={}", contractCode);
        MarketViopProperties.IsYatirim cfg = viopProperties.getIsyatirim();
        WebClient client = buildClient(cfg);
        Duration blockTimeout =
                Duration.ofMillis(cfg.getReadTimeoutMs() + (long) cfg.getConnectTimeoutMs());
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String body = client.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(cfg.getSnapshotPath())
                                .queryParam("endeks", contractCode)
                                .build())
                        .header(HttpHeaders.ACCEPT, ACCEPT)
                        .header(HttpHeaders.REFERER, cfg.getReferer())
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header(HttpHeaders.USER_AGENT, cfg.getUserAgent())
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(blockTimeout);
                log.info(
                        "VIOP_PROVIDER_SNAPSHOT_SUCCESS contractCode={} durationMs={} attempt={} dataQuality=OK",
                        contractCode,
                        System.currentTimeMillis() - t0,
                        attempt);
                return body;
            } catch (Exception e) {
                last = e;
                if (attempt < 2) {
                    log.warn(
                            "VIOP_PROVIDER_SNAPSHOT_RETRY contractCode={} attempt={} error={}",
                            contractCode,
                            attempt,
                            e.getMessage());
                    try {
                        Thread.sleep(450L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
        }
        log.warn(
                "VIOP_PROVIDER_SNAPSHOT_ERROR contractCode={} durationMs={} dataQuality=PROVIDER_ERROR error={}",
                contractCode,
                System.currentTimeMillis() - t0,
                last != null ? last.getMessage() : "unknown");
        if (last instanceof RuntimeException re) {
            throw re;
        }
        if (last != null) {
            throw new RuntimeException(last);
        }
        throw new IllegalStateException("VIOP snapshot fetch failed");
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
