package com.nurseli.nrsfinanceportal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Public login password-grant client özellikleri.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.keycloak.password-grant")
public class KeycloakPasswordGrantProperties {

    private String clientId = "nrs-frontend";
    private String clientSecret = "";
}
