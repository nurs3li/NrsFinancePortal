package com.nurseli.notificationservice.infrastructure.finance;

import com.nurseli.notificationservice.infrastructure.security.S2SAccessTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * finance-service internal HTTP API'sinden kullanıcı bilgisi çeker;
 * S2S JWT ile kimlik doğrulaması yapılır.
 */
@Component
public class FinanceUserClient {

    @Value("${finance.base-url}")
    private String financeBaseUrl;

    private final S2SAccessTokenService s2sAccessTokenService;
    private final WebClient webClient;

    @Autowired
    public FinanceUserClient(S2SAccessTokenService s2sAccessTokenService) {
        this(s2sAccessTokenService, WebClient.builder().build());
    }

    /**
     * {@code FinanceUserClient} — Test ve özel {@link WebClient} enjeksiyonu için paket görünümlü kurucu.
     */
    FinanceUserClient(S2SAccessTokenService s2sAccessTokenService, WebClient webClient) {
        this.s2sAccessTokenService = s2sAccessTokenService;
        this.webClient = webClient;
    }

    /**
     * {@code getBySub} — Keycloak {@code sub} değerine göre kullanıcı bilgisini döner;
     * kayıt bulunamazsa {@code null} döner.
     */
    public FinanceUserInfoResponse getBySub(String sub) {
        String accessToken = s2sAccessTokenService.getAccessToken();

        return webClient.get()
                .uri(financeBaseUrl + "/internal/users/by-sub/{sub}", sub)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .exchangeToMono(resp -> {
                    if (resp.statusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.empty();
                    }
                    if (resp.statusCode().isError()) {
                        return resp.createException().flatMap(Mono::error);
                    }
                    return resp.bodyToMono(FinanceUserInfoResponse.class);
                })
                .block();
    }
}
