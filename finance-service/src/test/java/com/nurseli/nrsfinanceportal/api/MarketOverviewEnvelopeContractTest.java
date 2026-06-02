package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.config.RateLimitProperties;
import com.nurseli.nrsfinanceportal.api.dto.MarketOverviewResponse;
import com.nurseli.nrsfinanceportal.infrastructure.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import com.nurseli.nrsfinanceportal.application.market.MarketOverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketOverviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("unused") // MockitoBean alanları Spring tarafından enjekte edilir; test gövdesinde doğrudan kullanılmayabilir.
class MarketOverviewEnvelopeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketOverviewService marketOverviewService;

    @MockitoBean
    private NotificationEventKafkaPublisher notificationEventKafkaPublisher;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @Test
    void shouldWrapOverviewResponseWithStandardEnvelope() throws Exception {
        when(marketOverviewService.getOverview())
                .thenReturn(new MarketOverviewResponse(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), LocalDateTime.now()));

        mockMvc.perform(get("/api/market/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.timestamp").exists());
    }
}
