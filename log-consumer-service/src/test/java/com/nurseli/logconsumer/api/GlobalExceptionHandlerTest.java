package com.nurseli.logconsumer.api;

import com.nurseli.logconsumer.api.response.ApiErrorCode;
import com.nurseli.logconsumer.api.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    request = requestWithPath("/actuator/health");
  }

  @Test
  void handleBadRequest_returnsEnvelopeWithCode() {
    ResponseEntity<ApiResponse<?>> res =
        handler.handleBadRequest(new IllegalArgumentException("invalid"), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    Map<String, Object> errors = castErrors(res.getBody());
    assertThat(errors.get("code")).isEqualTo(ApiErrorCode.BAD_REQUEST);
    assertThat(errors.get("message")).isEqualTo("invalid");
    assertThat(errors.get("path")).isEqualTo("/actuator/health");
  }

  @Test
  void handleResponseStatus_notFound_mapsCode() {
    ResponseEntity<ApiResponse<?>> res =
        handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(castErrors(res.getBody()).get("code")).isEqualTo(ApiErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void handleGeneric_returns500Envelope() {
    ResponseEntity<ApiResponse<?>> res = handler.handleGeneric(new RuntimeException("boom"), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(castErrors(res.getBody()).get("code")).isEqualTo(ApiErrorCode.INTERNAL_SERVER_ERROR);
  }

  @Test
  void resolveIllegalStateStatus_opensearchUnavailable_returns503() {
    assertThat(GlobalExceptionHandler.resolveIllegalStateStatus(
            new IllegalStateException("opensearch not configured")))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  private static HttpServletRequest requestWithPath(String path) {
    MockHttpServletRequest mock = new MockHttpServletRequest();
    mock.setRequestURI(path);
    return mock;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castErrors(ApiResponse<?> body) {
    assertThat(body).isNotNull();
    assertThat(body.isSuccess()).isFalse();
    return (Map<String, Object>) body.getErrors();
  }
}
