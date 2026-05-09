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

import com.nurseli.nrsfinanceportal.repository.UserRepository;

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

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRealmAndDbRoleAuthoritiesConverter(userRepository));
        return converter;
    }

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
                                "/api/public/register/**"
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