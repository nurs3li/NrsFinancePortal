package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.config.RateLimitProperties;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import com.nurseli.nrsfinanceportal.application.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.application.DashboardSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("unused") // MockitoBean alanları Spring tarafından enjekte edilir; test gövdesinde doğrudan kullanılmayabilir.
class DashboardEnvelopeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardSummaryService dashboardSummaryService;

    @MockitoBean
    private CurrentUserResolver currentUserResolver;

    @MockitoBean
    private NotificationEventKafkaPublisher notificationEventKafkaPublisher;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void shouldWrapDashboardSummaryWithStandardEnvelope() throws Exception {
        User current = mock(User.class);
        when(current.getId()).thenReturn(1L);
        when(currentUserResolver.getOrCreateCurrentUser()).thenReturn(current);

        var segment = new DashboardSummaryResponse.TradingSegmentSummary(
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE);
        DashboardSummaryResponse response = new DashboardSummaryResponse(
                new DashboardSummaryResponse.PortfolioSummary(
                        BigDecimal.ONE,
                        Map.of(),
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        List.of()
                ),
                new BigDecimal("2"),
                segment,
                segment
        );
        when(dashboardSummaryService.getSummary(1L)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPortfolioValueTry").exists());
    }
}
