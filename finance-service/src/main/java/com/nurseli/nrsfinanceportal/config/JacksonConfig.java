package com.nurseli.nrsfinanceportal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ObjectMapper ve JSON serileÅŸtirme ayarlarÄ±.
 */
@Configuration
public class JacksonConfig {

    /**
 * Uygulama genelinde kullanılan ObjectMapper bean.
 */

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java time (Instant, LocalDateTime vs)
        mapper.registerModule(new JavaTimeModule());

        // â— EN KRÄ°TÄ°K SATIR
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
