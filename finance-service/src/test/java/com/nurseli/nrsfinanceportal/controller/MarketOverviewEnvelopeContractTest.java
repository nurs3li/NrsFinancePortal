package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.config.RateLimitProperties;
import com.nurseli.nrsfinanceportal.dto.MarketOverviewResponse;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.service.MarketOverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketOverviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class MarketOverviewEnvelopeContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketOverviewService marketOverviewService;

    @MockBean
    private NotificationEventKafkaPublisher notificationEventKafkaPublisher;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
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
