package com.nurseli.notificationservice.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private NotificationRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new NotificationRateLimitService(redisTemplate);
    }

    @Test
    void allow_firstRequestWithinWindow_returnsTrue() {
        when(valueOperations.increment("rate:key")).thenReturn(1L);

        assertThat(rateLimitService.allow("rate:key", Duration.ofMinutes(1), 5)).isTrue();
        verify(redisTemplate).expire("rate:key", Duration.ofMinutes(1));
    }

    @Test
    void allow_exceedsMax_returnsFalse() {
        when(valueOperations.increment("rate:key")).thenReturn(6L);

        assertThat(rateLimitService.allow("rate:key", Duration.ofMinutes(1), 5)).isFalse();
    }

    @Test
    void allow_redisError_failsOpen() {
        when(valueOperations.increment(any())).thenThrow(new RuntimeException("redis"));

        assertThat(rateLimitService.allow("rate:key", Duration.ofMinutes(1), 5)).isTrue();
    }
}
