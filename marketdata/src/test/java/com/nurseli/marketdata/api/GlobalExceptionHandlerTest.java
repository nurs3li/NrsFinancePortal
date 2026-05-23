package com.nurseli.marketdata.api;

import com.nurseli.marketdata.api.exception.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    request = requestWithPath("/api/market/test");
  }

  @Test
  void invalidRequestShouldExposeBadRequestCode() {
    ResponseEntity<ApiEnvelope<?>> response =
        handler.handleInvalidRequest(new InvalidRequestException("bad"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    Map<String, Object> body = castErrors(response.getBody());
    assertThat(body.get("code")).isEqualTo(ErrorCode.BAD_REQUEST);
    assertThat(body.get("message")).isEqualTo("bad");
    assertThat(body.get("path")).isEqualTo("/api/market/test");
    assertThat(body.get("timestamp")).isNotNull();
  }

  @Test
  void genericErrorShouldExposeInternalCode() {
    ResponseEntity<ApiEnvelope<?>> response =
        handler.handleGeneric(new RuntimeException("boom"), request);

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    Map<String, Object> body = castErrors(response.getBody());
    assertThat(body.get("code")).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    assertThat(body.get("error")).isEqualTo("Internal server error");
  }

  @Test
  void handleForbidden_returns403Envelope() {
    ResponseEntity<ApiEnvelope<?>> response =
        handler.handleForbidden(new AccessDeniedException("denied"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(castErrors(response.getBody()).get("code")).isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @ParameterizedTest
  @CsvSource({
    "NOT_FOUND, RESOURCE_NOT_FOUND",
    "SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE"
  })
  void handleResponseStatus_mapsHttpStatusToErrorCode(HttpStatus status, String expectedCode) {
    ResponseEntity<ApiEnvelope<?>> response =
        handler.handleResponseStatus(new ResponseStatusException(status, "reason"), request);

    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(castErrors(response.getBody()).get("code")).isEqualTo(expectedCode);
  }

  @Test
  void handleValidation_returnsFirstFieldError() throws Exception {
    Method method =
        GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleValidatedMethod", String.class);
    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new FieldError("request", "symbol", "must not be blank"));
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(new org.springframework.core.MethodParameter(method, 0), bindingResult);

    ResponseEntity<ApiEnvelope<?>> response = handler.handleValidation(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(castErrors(response.getBody()).get("code")).isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(castErrors(response.getBody()).get("message")).isEqualTo("symbol: must not be blank");
  }

  @Test
  void resolveIllegalStateStatus_notConfigured_returns503() {
    assertThat(GlobalExceptionHandler.resolveIllegalStateStatus(
            new IllegalStateException("EVDS is disabled")))
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @SuppressWarnings("unused")
  private void sampleValidatedMethod(String symbol) {}

  private static HttpServletRequest requestWithPath(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    return request;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castErrors(ApiEnvelope<?> envelope) {
    assertThat(envelope).isNotNull();
    assertThat(envelope.isSuccess()).isFalse();
    return (Map<String, Object>) envelope.getErrors();
  }
}
