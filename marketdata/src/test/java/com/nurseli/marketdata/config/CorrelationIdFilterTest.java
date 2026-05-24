package com.nurseli.marketdata.config;

import jakarta.servlet.FilterChain;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @BeforeEach
  @AfterEach
  void clearContext() {
    MDC.clear();
    ThreadContext.clearAll();
  }

  @Test
  void doFilter_reusesIncomingHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "corr-md");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("corr-md");
  }

  @Test
  void doFilter_clearsMdcAfterChain() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "trace-me");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) -> assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isEqualTo("trace-me"));

    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }
}
