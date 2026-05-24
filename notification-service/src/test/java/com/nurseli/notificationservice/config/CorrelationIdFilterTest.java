package com.nurseli.notificationservice.config;

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
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "  corr-123  ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, noopChain());

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("corr-123");
  }

  @Test
  void doFilter_generatesUuidWhenHeaderMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, noopChain());

    String generated = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(generated).isNotBlank();
    assertThat(generated).matches("[0-9a-f\\-]{36}");
  }

  @Test
  void doFilter_setsMdcDuringChainAndClearsAfter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "trace-me");
    MockHttpServletResponse response = new MockHttpServletResponse();
    String[] mdcDuringChain = new String[1];

    FilterChain chain =
        (req, res) -> {
          mdcDuringChain[0] = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          assertThat(ThreadContext.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY))
              .isEqualTo("trace-me");
        };

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain[0]).isEqualTo("trace-me");
    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
    assertThat(ThreadContext.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  private static FilterChain noopChain() {
    return (req, res) -> {};
  }
}
