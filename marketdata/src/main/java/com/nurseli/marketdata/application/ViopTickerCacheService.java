package com.nurseli.marketdata.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.marketdata.config.ViopHybridProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViopTickerCacheService {
    private final ViopHybridProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public void cache(ViopHybridAggregationService.HybridViopRow row) {
        if (!properties.isEnabled() || !properties.isRedisEnabled() || row == null || row.contractCode() == null) {
            return;
        }
        try {
            String key = "viop:ticker:" + row.contractCode();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "contractCode", row.contractCode(),
                    "price", row.price(),
                    "openInterest", row.openInterest(),
                    "quality", row.quality(),
                    "asOf", row.asOf()
            ));
            redisTemplate.opsForValue().set(key, payload, Math.max(1, properties.getRedisTtlSeconds()), TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("[VIOP_CACHE] cache write failed for {}: {}", row.contractCode(), ex.getMessage());
        }
    }
}

