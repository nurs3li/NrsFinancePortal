package com.nurseli.notificationservice.api;

import com.nurseli.notificationservice.api.response.ApiErrorCode;
import com.nurseli.notificationservice.api.response.ApiResponse;
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
    request = requestWithPath("/api/notifications/me");
  }

  @Test
  void handleBadRequest_returnsEnvelopeWithCode() {
    ResponseEntity<ApiResponse<?>> res =
        handler.handleBadRequest(new IllegalArgumentException("invalid"), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    Map<String, Object> errors = castErrors(res.getBody());
    assertThat(errors.get("code")).isEqualTo(ApiErrorCode.BAD_REQUEST);
    assertThat(errors.get("message")).isEqualTo("invalid");
    assertThat(errors.get("error")).isEqualTo("invalid");
    assertThat(errors.get("path")).isEqualTo("/api/notifications/me");
  }

  @ParameterizedTest
  @CsvSource({
    "gmail oauth token alınamadı, BAD_GATEWAY",
    "gmail fromaddress is not configured, SERVICE_UNAVAILABLE",
    "jwt authentication required, UNAUTHORIZED",
    "something else broke, INTERNAL_SERVER_ERROR"
  })
  void resolveIllegalStateStatus_mapsByMessage(String message, String expectedStatus) {
    assertThat(GlobalExceptionHandler.resolveIllegalStateStatus(new IllegalStateException(message)))
        .isEqualTo(HttpStatus.valueOf(expectedStatus));
  }

  @Test
  void handleIllegalState_gmailFailure_returnsBadGatewayEnvelope() {
    request = requestWithPath("/api/notifications/internal/email/send");
    ResponseEntity<ApiResponse<?>> res =
        handler.handleIllegalState(
            new IllegalStateException("Gmail OAuth token alınamadı"), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(castErrors(res.getBody()).get("code")).isEqualTo(ApiErrorCode.BAD_GATEWAY);
  }

  @Test
  void handleForbidden_returns403Envelope() {
    ResponseEntity<ApiResponse<?>> res =
        handler.handleForbidden(new AccessDeniedException("nope"), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    Map<String, Object> errors = castErrors(res.getBody());
    assertThat(errors.get("code")).isEqualTo(ApiErrorCode.ACCESS_DENIED);
    assertThat(errors.get("message")).isEqualTo("Access denied");
  }

  @Test
  void handleGeneric_sslRootCause_returns502WithTurkishMessage() {
    Exception ex = new Exception(new RuntimeException("SSL handshake failed"));

    ResponseEntity<ApiResponse<?>> res = handler.handleGeneric(ex, request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(castErrors(res.getBody()).get("code")).isEqualTo(ApiErrorCode.BAD_GATEWAY);
    assertThat(castErrors(res.getBody()).get("message").toString()).contains("Google");
  }

  @Test
  void handleValidation_returnsFirstFieldError() throws Exception {
    Method method =
        InternalEmailController.class.getDeclaredMethod(
            "sendEmail", InternalEmailController.SendEmailRequest.class);
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new FieldError("request", "to", "must not be blank"));
    MethodArgumentNotValidException ex =
        new MethodArgumentNotValidException(new org.springframework.core.MethodParameter(method, 0), bindingResult);

    ResponseEntity<ApiResponse<?>> res = handler.handleValidation(ex, request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    Map<String, Object> errors = castErrors(res.getBody());
    assertThat(errors.get("code")).isEqualTo(ApiErrorCode.VALIDATION_ERROR);
    assertThat(errors.get("message")).isEqualTo("to: must not be blank");
  }

  @ParameterizedTest
  @CsvSource({
    "NOT_FOUND, RESOURCE_NOT_FOUND",
    "SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE",
    "BAD_GATEWAY, BAD_GATEWAY"
  })
  void handleResponseStatus_mapsHttpStatusToErrorCode(HttpStatus status, String expectedCode) {
    ResponseEntity<ApiResponse<?>> res =
        handler.handleResponseStatus(new ResponseStatusException(status, "reason"), request);

    assertThat(res.getStatusCode()).isEqualTo(status);
    assertThat(castErrors(res.getBody()).get("code")).isEqualTo(expectedCode);
    assertThat(castErrors(res.getBody()).get("message")).isEqualTo("reason");
  }

  @Test
  void handleGeneric_unknownError_returns500Envelope() {
    ResponseEntity<ApiResponse<?>> res =
        handler.handleGeneric(new RuntimeException("boom"), request);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(castErrors(res.getBody()).get("code")).isEqualTo(ApiErrorCode.INTERNAL_SERVER_ERROR);
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
