package com.nurseli.marketdata.infrastructure.tefas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.TefasProperties;
import com.nurseli.marketdata.infrastructure.tefas.TefasApiModels.TefasEnvelope;
import com.nurseli.marketdata.infrastructure.tefas.TefasApiModels.TefasPriceRow;
import com.nurseli.marketdata.infrastructure.tefas.TefasApiModels.TefasReturnRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TefasClient {

    private static final List<Integer> VALID_PERIOD_MONTHS = List.of(1, 3, 6, 12, 36, 60);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final TefasProperties properties;

    public TefasClient(TefasProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        // TEFAS fon listesi JSON'u 256KB varsayılan buffer limitini aşabiliyor.
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
        this.webClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .defaultHeader("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
                .defaultHeader("Origin", properties.getBaseUrl())
                .defaultHeader("Referer", properties.getBaseUrl() + "/")
                .build();
    }

    public List<TefasReturnRow> fetchReturnBasedList() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dil", "TR");
        payload.put("fonTipi", properties.getFundKind());
        payload.put("kurucuKodu", null);
        payload.put("sfonTurKod", null);
        payload.put("fonTurAciklama", null);
        payload.put("islem", 1);
        payload.put("fonTurKod", null);
        payload.put("fonGrubu", null);
        payload.put("donemGetiri1a", "1");
        payload.put("donemGetiri3a", "1");
        payload.put("donemGetiri6a", "1");
        payload.put("donemGetiri1y", "1");
        payload.put("donemGetiriyb", "1");
        payload.put("donemGetiri3y", "1");
        payload.put("donemGetiri5y", "1");
        payload.put("basTarih", null);
        payload.put("bitTarih", null);
        payload.put("calismaTipi", 2);
        payload.put("getiriOrani", "1");
        return postList("/api/funds/fonGetiriBazliBilgiGetir", payload, new TypeReference<TefasEnvelope<TefasReturnRow>>() {});
    }

    public List<TefasPriceRow> fetchPriceHistory(String fundCode, int periodMonths) {
        if (!properties.isEnabled() || fundCode == null || fundCode.isBlank()) {
            return List.of();
        }
        int period = snapPeriodMonths(periodMonths);
        Map<String, Object> payload = Map.of(
                "fonKodu", fundCode.trim().toUpperCase(),
                "dil", "TR",
                "periyod", period);
        return postList("/api/funds/fonFiyatBilgiGetir", payload, new TypeReference<TefasEnvelope<TefasPriceRow>>() {});
    }

    private <T> List<T> postList(String path, Object payload, TypeReference<TefasEnvelope<T>> type) {
        try {
            String json = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .onErrorResume(ex -> {
                        log.warn("[TEFAS] POST {} failed: {}", path, ex.toString());
                        return Mono.just("");
                    })
                    .block(Duration.ofMillis(properties.getReadTimeoutMs() + 2_000L));
            if (json == null || json.isBlank()) {
                return List.of();
            }
            TefasEnvelope<T> envelope = objectMapper.readValue(json, type);
            return envelope != null && envelope.resultList() != null ? envelope.resultList() : List.of();
        } catch (Exception ex) {
            log.warn("[TEFAS] parse error path={}: {}", path, ex.toString());
            return List.of();
        }
    }

    static int snapPeriodMonths(int requested) {
        int want = Math.max(1, requested);
        for (int p : VALID_PERIOD_MONTHS) {
            if (p >= want) {
                return p;
            }
        }
        return 60;
    }
}
