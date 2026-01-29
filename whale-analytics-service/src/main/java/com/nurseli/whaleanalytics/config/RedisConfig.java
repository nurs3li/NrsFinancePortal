package com.nurseli.whaleanalytics.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableRedisRepositories
public class RedisConfig {
    // Bilerek BOŞ
    // StringRedisTemplate Spring Boot tarafından otomatik sağlanır
}
