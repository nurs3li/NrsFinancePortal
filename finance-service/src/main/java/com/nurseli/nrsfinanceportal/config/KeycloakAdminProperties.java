package com.nurseli.nrsfinanceportal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Realm'de kullanıcıyı disable/enable etmek için confidential client + service account.
 * Keycloak Admin Console: Client oluştur → Client authentication ON, Service accounts ON →
 * service account kullanıcısına realm-management → manage-users (veya daha dar) ata.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.keycloak.admin")
public class KeycloakAdminProperties {

    /** Confidential client için true; yanlışsa suspend-login beklenmedik şekilde çalışmaz. */
    private boolean enabled = false;

    /**
     * Keycloak sunucusu (dockersız: http://localhost:8081 ; docker içi: http://nrs-keycloak:8080).
     */
    private String serverUrl = "http://localhost:8081";

    private String realm = "nrs-finance";

    private String clientId = "";

    private String clientSecret = "";
}
