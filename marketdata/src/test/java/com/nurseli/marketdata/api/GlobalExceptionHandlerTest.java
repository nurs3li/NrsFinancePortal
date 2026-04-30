package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidRequestShouldExposeBadRequestCode() {
        ResponseEntity<ApiEnvelope<?>> response = handler.handleInvalidRequest(new InvalidRequestException("bad"), request("/api/test"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        Map<String, Object> body = castErrors(response.getBody());
        assertThat(body.get("code")).isEqualTo("BAD_REQUEST");
        assertThat(body.get("message")).isEqualTo("bad");
    }

    @Test
    void genericErrorShouldExposeInternalCode() {
        ResponseEntity<ApiEnvelope<?>> response = handler.handleGeneric(new RuntimeException("boom"), request("/api/test"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        Map<String, Object> body = castErrors(response.getBody());
        assertThat(body.get("code")).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(body.get("error")).isEqualTo("Internal server error");
    }

    private static HttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castErrors(ApiEnvelope<?> envelope) {
        assertThat(envelope).isNotNull();
        return (Map<String, Object>) envelope.getErrors();
    }
}
