package com.nurseli.nrsfinanceportal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.keycloak.security")
public class KeycloakSecurityProperties {

    private boolean otpEnforcementEnabled = false;
    /**
     * true iken (Admin API + cron) realm'de rememberMe + SSO/client oturum süreleri {@link #rememberMe} ve timeout alanlarıyla hizalanır.
     * Keycloak konsolunda Login → Remember me ayrıca açık olmalı; kullanıcı kutuyu işaretleyebilsin.
     * Env: {@code KEYCLOAK_SECURITY_REMEMBER_ME_ENFORCEMENT_ENABLED}
     */
    private boolean rememberMeEnforcementEnabled = false;
    private List<String> enforcedRoles = List.of("ADMIN");
    private String requiredAction = "CONFIGURE_TOTP";
    private boolean rememberMe = true;
    private Duration ssoIdle = Duration.ofMinutes(30);
    private Duration ssoMax = Duration.ofHours(8);
    private Duration accessTokenLifespan = Duration.ofMinutes(5);
    private Duration clientSessionIdle = Duration.ofMinutes(30);
    private Duration clientSessionMax = Duration.ofHours(8);
    private String reconcileCron = "0 0 */6 * * *";
}
