package com.nurseli.nrsfinanceportal.config;

import com.nurseli.nrsfinanceportal.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * Keycloak realm_access rollerini Spring {@code ROLE_*} olarak yükler.
 * <p>
 * Keycloak'ta composite / token ayarı yüzünden JWT'de {@code USER} görünmeyebilir;
 * bu durumda {@code @PreAuthorize(hasRole('USER'))} 403 verirken arayüz DB'den USER gösterir.
 * JWT'de uygulama rollerinden hiçbiri yoksa, kayıtlı kullanıcının DB rolü eklenir.
 */
public class JwtRealmAndDbRoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Set<String> APP_ROLE_AUTHORITIES = Set.of(
            "ROLE_USER",
            "ROLE_FINANCE_MANAGER",
            "ROLE_ADMIN"
    );

    private final KeycloakJwtGrantedAuthoritiesConverter keycloak = new KeycloakJwtGrantedAuthoritiesConverter();
    private final UserRepository userRepository;

    public JwtRealmAndDbRoleAuthoritiesConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>(keycloak.convert(jwt));
        if (hasAnyAppRole(authorities)) {
            return authorities;
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return authorities;
        }
        userRepository.findByKeycloakUserId(sub)
                .ifPresent(user -> authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        if (!hasAnyAppRole(authorities)) {
            // New Keycloak users may arrive without mapped realm role in first token.
            // Keep first-login flow working by granting baseline USER authority.
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return authorities;
    }

    private static boolean hasAnyAppRole(Collection<GrantedAuthority> authorities) {
        for (GrantedAuthority a : authorities) {
            if (APP_ROLE_AUTHORITIES.contains(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
