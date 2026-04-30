package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.config.RateLimitProperties;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.DashboardSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardEnvelopeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardSummaryService dashboardSummaryService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private NotificationEventKafkaPublisher notificationEventKafkaPublisher;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void shouldWrapDashboardSummaryWithStandardEnvelope() throws Exception {
        User current = mock(User.class);
        when(current.getId()).thenReturn(1L);
        when(currentUserResolver.getOrCreateCurrentUser()).thenReturn(current);

        DashboardSummaryResponse response = new DashboardSummaryResponse(
                new DashboardSummaryResponse.WhaleSummary("NONE", 0, Instant.now()),
                new DashboardSummaryResponse.CashSummary(BigDecimal.TEN),
                new DashboardSummaryResponse.PortfolioSummary(
                        BigDecimal.ONE,
                        Map.of(),
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        List.of()
                ),
                new DashboardSummaryResponse.ActivitySummary(Instant.now(), 0),
                BigDecimal.ONE
        );
        when(dashboardSummaryService.getSummary(1L)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.netWorthTry").exists());
    }
}
