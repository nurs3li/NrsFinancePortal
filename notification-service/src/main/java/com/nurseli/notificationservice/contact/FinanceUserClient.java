package com.nurseli.notificationservice.contact;

import com.nurseli.notificationservice.security.S2SAccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FinanceUserClient {

    @Value("${finance.base-url}")
    private String financeBaseUrl;

    private final S2SAccessTokenService s2sAccessTokenService;

    private final WebClient webClient = WebClient.builder().build();

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