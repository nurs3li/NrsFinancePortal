package com.nurseli.logconsumer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Redis repository desteğini etkinleştirir; {@code StringRedisTemplate} Spring Boot auto-config ile sağlanır.
 */
@Configuration
@EnableRedisRepositories
public class RedisConfig {
}
