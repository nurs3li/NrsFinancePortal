package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.identity.JwtIdentityReader;
import com.nurseli.nrsfinanceportal.config.KeycloakSecurityProperties;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakAdminTokenProvider;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakRealmSecurityClient;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class CurrentUserResolver {

    private final JwtIdentityReader jwtIdentityReader;
    private final UserRepository userRepository;
    private final UserRegistrationNotificationHelper userRegistrationNotificationHelper;
    private final KeycloakSecurityProperties keycloakSecurityProperties;
    private final KeycloakAdminTokenProvider keycloakAdminTokenProvider;
    private final KeycloakRealmSecurityClient keycloakRealmSecurityClient;

    @Transactional
    public User getOrCreateCurrentUser() {

        String keycloakUserId = jwtIdentityReader.getRequiredSubject();
        String email = jwtIdentityReader.getEmail();
        String username = jwtIdentityReader.getUsername();
        Role jwtRole = jwtIdentityReader.getRealmRole();
        Boolean emailVerified = jwtIdentityReader.getEmailVerified();

        Optional<User> existing = userRepository.findByKeycloakUserId(keycloakUserId);
        User user;
        if (existing.isPresent()) {
            user = existing.get();
        } else {
            user = createNewUser(keycloakUserId, email, username, jwtRole);
            userRegistrationNotificationHelper.notifyAdminsNewUser(user, Instant.now());
        }

        boolean changed = false;

        if (user.getRole() != jwtRole) {
            user.setRole(jwtRole);
            changed = true;
        }

        if (emailVerified != null) {
            boolean before = user.isEmailVerified();
            user.setEmailVerified(emailVerified);
            if (before != user.isEmailVerified()) {
                changed = true;
            }
        }

        if (email != null && !email.equals(user.getEmail())) {
            user.setEmail(email);
            changed = true;
        }

        String givenName = readJwtClaim("given_name");
        String familyName = readJwtClaim("family_name");
        if (givenName != null && !givenName.equals(user.getFirstName())) {
            user.setFirstName(givenName);
            changed = true;
        }
        if (familyName != null && !familyName.equals(user.getLastName())) {
            user.setLastName(familyName);
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
        }
        enforceOtpRequiredActionIfNeeded(keycloakUserId, jwtRole);

        return user;
    }

    private void enforceOtpRequiredActionIfNeeded(String keycloakUserId, Role effectiveRole) {
        if (!keycloakSecurityProperties.isOtpEnforcementEnabled()) return;
        if (!keycloakAdminTokenProvider.isConfigured()) return;
        if (keycloakUserId == null || keycloakUserId.isBlank()) return;

        List<String> enforcedRoles = keycloakSecurityProperties.getEnforcedRoles();
        if (enforcedRoles == null || enforcedRoles.isEmpty()) return;
        String roleName = effectiveRole != null ? effectiveRole.name() : Role.USER.name();
        boolean roleEnforced = enforcedRoles.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .anyMatch(v -> v.equals(roleName));
        if (!roleEnforced) return;

        String requiredAction = keycloakSecurityProperties.getRequiredAction();
        String action = (requiredAction == null || requiredAction.isBlank())
                ? "CONFIGURE_TOTP"
                : requiredAction.trim().toUpperCase(Locale.ROOT);
        try {
            if (keycloakRealmSecurityClient.hasOtpCredential(keycloakUserId)) {
                keycloakRealmSecurityClient.removeRequiredActionIfPresent(keycloakUserId, action);
                return;
            }
            keycloakRealmSecurityClient.addRequiredActionIfMissing(keycloakUserId, action);
        } catch (Exception ex) {
            log.warn("[KEYCLOAK_SECURITY][login] otp_required_action_sync_failed userId={} role={} reason={}",
                    keycloakUserId, roleName, ex.getMessage());
        }
    }

    private String readJwtClaim(String claim) {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt)) {
            return null;
        }
        String value = jwt.getClaimAsString(claim);
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private User createNewUser(String keycloakUserId, String email, String username, Role role) {
        User user = User.createFromIdentity(keycloakUserId, email, username, role);
        user.setFirstName(readJwtClaim("given_name"));
        user.setLastName(readJwtClaim("family_name"));
        return userRepository.save(user);
    }

    public Long getCurrentUserId() {
        return getOrCreateCurrentUser().getId();
    }
}
