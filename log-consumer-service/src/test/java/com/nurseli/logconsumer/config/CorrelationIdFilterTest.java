package com.nurseli.logconsumer.config;

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
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void doFilter_generatesCorrelationIdWhenMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    String generated = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
    assertThat(generated).isNotBlank();
    assertThat(generated).matches("[0-9a-f\\-]{36}");
  }
}
