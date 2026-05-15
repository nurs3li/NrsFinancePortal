package com.nurseli.nrsfinanceportal.infrastructure.client.market;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * market-data internal backfill uçları ({@code X-Nrs-Internal-Token}) — tarayıcı JWT / issuer
 * uyumsuzluğu olmadan BIST ve İş Yatırım USD maden doldurma.
 */
@Component
public class MarketDataInternalBackfillGateway {

    private static final String INTERNAL_HEADER = "X-Nrs-Internal-Token";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient backfillClient;
    private final String internalToken;

    public MarketDataInternalBackfillGateway(
            @Qualifier("marketDataBackfillWebClient") WebClient backfillClient,
            @Value("${market-data.internal-backfill-token:}") String internalToken
    ) {
        this.backfillClient = backfillClient;
        this.internalToken = internalToken;
    }

    private void requireToken() {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "market-data.internal-backfill-token (NRS_INTERNAL_BACKFILL_TOKEN) tanımlı değil; "
                            + "finance ve market-data aynı paylaşımlı anahtarı kullanmalı."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static Object unwrapMarketDataEnvelope(Object body) {
        if (!(body instanceof Map<?, ?> map)) {
            return body;
        }
        Object success = map.get("success");
        if (Boolean.TRUE.equals(success) && map.get("data") != null) {
            return map.get("data");
        }
        return body;
    }

    public Object triggerIsyatirimMetalsUsd(String symbols, LocalDate from, LocalDate to, boolean force) {
        requireToken();
        try {
            Map<String, Object> body = backfillClient
                    .post()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/internal/market/backfill/isyatirim-metals-usd");
                        if (symbols != null && !symbols.isBlank()) {
                            b = b.queryParam("symbols", symbols);
                        }
                        if (from != null) {
                            b = b.queryParam("from", from);
                        }
                        if (to != null) {
                            b = b.queryParam("to", to);
                        }
                        b = b.queryParam("force", force);
                        return b.build();
                    })
                    .header(INTERNAL_HEADER, internalToken)
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .block(Duration.ofMinutes(6));
            return unwrapMarketDataEnvelope(body != null ? body : Map.of());
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "market-data İş Yatırım maden backfill HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    public Object triggerBistDaily(Map<String, Object> bodyOrNull) {
        requireToken();
        Map<String, Object> payload = bodyOrNull != null ? new LinkedHashMap<>(bodyOrNull) : new LinkedHashMap<>();
        try {
            Map<String, Object> body = backfillClient
                    .post()
                    .uri("/internal/market/backfill/bist-daily")
                    .header(INTERNAL_HEADER, internalToken)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(MAP_TYPE)
                    .block(Duration.ofMinutes(15));
            return unwrapMarketDataEnvelope(body != null ? body : Map.of());
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "market-data BIST backfill HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    e
            );
        }
    }
}
