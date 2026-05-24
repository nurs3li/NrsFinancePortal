package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(mock(NotificationEventKafkaPublisher.class), mock(UserRepository.class));
    }

    @Test
    void badRequestShouldIncludeCodeAndMessage() {
        HttpServletRequest request = request("/api/test");

        ResponseEntity<ApiResponse<?>> response = handler.handleBadRequest(new IllegalArgumentException("invalid"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<String, Object> errors = castErrors(response.getBody());
        assertThat(errors.get("code")).isEqualTo("BAD_REQUEST");
        assertThat(errors.get("message")).isEqualTo("invalid");
        assertThat(errors.get("error")).isEqualTo("invalid");
    }

    @Test
    void illegalStateShouldReturnBadRequest() {
        HttpServletRequest request = request("/api/resource");

        ResponseEntity<ApiResponse<?>> response =
                handler.handleIllegalState(new IllegalStateException("not found"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<String, Object> errors = castErrors(response.getBody());
        assertThat(errors.get("code")).isEqualTo("BAD_REQUEST");
        assertThat(errors.get("message")).isEqualTo("not found");
    }

    @Test
    void keycloakAuthIllegalStateShouldReturnBadGateway() {
        HttpServletRequest request = request("/api/public/register/request-code");

        ResponseEntity<ApiResponse<?>> response = handler.handleIllegalState(
                new IllegalStateException("Keycloak token 401 UNAUTHORIZED"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
    }

    private static HttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castErrors(ApiResponse<?> responseBody) {
        assertThat(responseBody).isNotNull();
        return (Map<String, Object>) responseBody.getErrors();
    }
}
