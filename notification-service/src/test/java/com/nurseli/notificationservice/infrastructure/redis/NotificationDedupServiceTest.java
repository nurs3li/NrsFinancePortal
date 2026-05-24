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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDedupServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private NotificationDedupService dedupService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        dedupService = new NotificationDedupService(redisTemplate);
    }

    @Test
    void firstTime_setIfAbsentTrue_returnsTrue() {
        when(valueOperations.setIfAbsent(eq("key-1"), eq("1"), any(Duration.class))).thenReturn(true);

        assertThat(dedupService.firstTime("key-1", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void firstTime_duplicate_returnsFalse() {
        when(valueOperations.setIfAbsent(eq("key-1"), eq("1"), any(Duration.class))).thenReturn(false);

        assertThat(dedupService.firstTime("key-1", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void firstTime_redisError_failsOpen() {
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(dedupService.firstTime("key-1", Duration.ofMinutes(5))).isTrue();
    }
}
