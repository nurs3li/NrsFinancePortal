package com.nurseli.nrsfinanceportal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Keycloak Admin REST WebClient bean.
 */
@Configuration
@EnableConfigurationProperties({KeycloakAdminProperties.class, KeycloakSecurityProperties.class, NotificationClientProperties.class})
public class KeycloakAdminConfiguration {

    /**
 * Keycloak Admin REST Ã§aÄŸrÄ±larÄ± iÃ§in WebClient bean.
 */

    @Bean
    public WebClient keycloakAdminWebClient(WebClient.Builder builder) {
        return builder.build();
    }
}
