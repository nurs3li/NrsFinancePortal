package com.nurseli.nrsfinanceportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import com.nurseli.nrsfinanceportal.config.ApiPaths;

/**
 * Spring Security filter chain; JWT resource server, CORS ve frozen-user kontrolÃƒÂ¼.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    private final AuditContextMdcFilter auditContextMdcFilter;
    private final FrozenUserAccessFilter frozenUserAccessFilter;
    private final UserRepository userRepository;

    public SecurityConfig(
            AuditContextMdcFilter auditContextMdcFilter,
            FrozenUserAccessFilter frozenUserAccessFilter,
            UserRepository userRepository) {
        this.auditContextMdcFilter = auditContextMdcFilter;
        this.frozenUserAccessFilter = frozenUserAccessFilter;
        this.userRepository = userRepository;
    }

    /**
 * JWT'den realm + DB rollerini GrantedAuthority olarak baÃ„Å¸lar.
 */

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRealmAndDbRoleAuthoritiesConverter(userRepository));
        return converter;
    }

    /**
 * OAuth2 resource server, permitAll public endpoint'ler ve MDC/frozen-user filter sÃ„Â±rasÃ„Â±nÃ„Â± yapÃ„Â±landÃ„Â±rÃ„Â±r.
 */

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                ApiPaths.v1FromLegacy("/api/public/register/**"),
                                "/api/public/register/**",
                                ApiPaths.v1FromLegacy("/api/public/login"),
                                "/api/public/login",
                                ApiPaths.v1FromLegacy("/api/public/login/**"),
                                "/api/public/login/**",
                                ApiPaths.v1FromLegacy("/api/public/token/**"),
                                "/api/public/token/**"
                        ).permitAll()
                        .requestMatchers(req -> "OPTIONS".equalsIgnoreCase(req.getMethod())).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterAfter(auditContextMdcFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(frozenUserAccessFilter, AuditContextMdcFilter.class);
        return http.build();
    }
}
