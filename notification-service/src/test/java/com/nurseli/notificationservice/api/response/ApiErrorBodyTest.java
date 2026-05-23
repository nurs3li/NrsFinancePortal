package com.nurseli.notificationservice.api.response;

import com.nurseli.notificationservice.config.CorrelationIdFilter;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorBodyTest {

  @AfterEach
  void clearThreadContext() {
    ThreadContext.clearAll();
  }

  @Test
  void of_includesCodeMessagePathAndTimestamp() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/me");

    Map<String, Object> body = ApiErrorBody.of(ApiErrorCode.BAD_REQUEST, "invalid", request);

    assertThat(body.get("code")).isEqualTo(ApiErrorCode.BAD_REQUEST);
    assertThat(body.get("message")).isEqualTo("invalid");
    assertThat(body.get("error")).isEqualTo("invalid");
    assertThat(body.get("path")).isEqualTo("/api/notifications/me");
    assertThat(body.get("timestamp")).isNotNull();
  }

  @Test
  void of_prefersMdcCorrelationIdOverHeader() {
    ThreadContext.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "mdc-corr");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "header-corr");

    Map<String, Object> body = ApiErrorBody.of(ApiErrorCode.UNAUTHORIZED, "auth", request);

    assertThat(body.get("correlationId")).isEqualTo("mdc-corr");
  }

  @Test
  void of_fallsBackToRequestHeaderWhenMdcEmpty() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "header-only");

    Map<String, Object> body = ApiErrorBody.of(ApiErrorCode.ACCESS_DENIED, "denied", request);

    assertThat(body.get("correlationId")).isEqualTo("header-only");
  }

  @Test
  void of_nullRequestLeavesPathAndCorrelationNull() {
    Map<String, Object> body = ApiErrorBody.of(ApiErrorCode.INTERNAL_SERVER_ERROR, "boom", null);

    assertThat(body.get("path")).isNull();
    assertThat(body.get("correlationId")).isNull();
  }
}
