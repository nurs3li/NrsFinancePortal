package com.nurseli.marketdata.api;

import com.nurseli.marketdata.config.CorrelationIdFilter;
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
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/market/fx");

    Map<String, Object> body = ApiErrorBody.of(ErrorCode.BAD_REQUEST, "invalid", request);

    assertThat(body.get("code")).isEqualTo(ErrorCode.BAD_REQUEST);
    assertThat(body.get("message")).isEqualTo("invalid");
    assertThat(body.get("path")).isEqualTo("/api/market/fx");
    assertThat(body.get("timestamp")).isNotNull();
  }

  @Test
  void of_prefersMdcCorrelationIdOverHeader() {
    ThreadContext.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "mdc-corr");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "header-corr");

    Map<String, Object> body = ApiErrorBody.of(ErrorCode.UNAUTHORIZED, "auth", request);

    assertThat(body.get("correlationId")).isEqualTo("mdc-corr");
  }
}
