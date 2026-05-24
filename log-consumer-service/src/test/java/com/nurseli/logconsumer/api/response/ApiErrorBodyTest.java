package com.nurseli.logconsumer.api.response;

import com.nurseli.logconsumer.config.CorrelationIdFilter;
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
  void of_includesStandardFields() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

    Map<String, Object> body = ApiErrorBody.of(ApiErrorCode.BAD_REQUEST, "invalid", request);

    assertThat(body.get("code")).isEqualTo(ApiErrorCode.BAD_REQUEST);
    assertThat(body.get("message")).isEqualTo("invalid");
    assertThat(body.get("path")).isEqualTo("/actuator/health");
    assertThat(body.get("timestamp")).isNotNull();
  }

  @Test
  void of_usesMdcCorrelationId() {
    ThreadContext.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "corr-99");
    MockHttpServletRequest request = new MockHttpServletRequest();

    Map<String, Object> body = ApiErrorBody.of(ApiErrorCode.INTERNAL_SERVER_ERROR, "boom", request);

    assertThat(body.get("correlationId")).isEqualTo("corr-99");
  }
}
