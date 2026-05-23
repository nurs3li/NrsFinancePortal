package com.nurseli.notificationservice.support;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public final class WebClientTestSupport {

    private WebClientTestSupport() {}

    public static WebClient jsonClient(HttpStatus status, String jsonBody) {
        ExchangeFunction exchange =
                request ->
                        Mono.just(
                                ClientResponse.create(status)
                                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                        .body(jsonBody)
                                        .build());
        return WebClient.builder().exchangeFunction(exchange).build();
    }
}
