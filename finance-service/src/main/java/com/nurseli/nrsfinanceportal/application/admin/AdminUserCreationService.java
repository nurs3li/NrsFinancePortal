package com.nurseli.nrsfinanceportal.application.admin;

import com.nurseli.nrsfinanceportal.api.dto.CreateUserRequest;
import com.nurseli.nrsfinanceportal.application.auth.KeycloakTotpLoginPolicyService;
import com.nurseli.nrsfinanceportal.application.user.UserSyncService;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakAdminTokenProvider;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakRealmRoleMappingClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserRegistrationClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Admin tarafından Keycloak Admin API ile kullanıcı oluşturma.
 */
@RequiredArgsConstructor
@Service
public class AdminUserCreationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");

    private final UserRepository userRepository;
    private final KeycloakAdminTokenProvider keycloakAdminTokenProvider;
    private final KeycloakUserRegistrationClient keycloakUserRegistrationClient;
    private final KeycloakRealmRoleMappingClient keycloakRealmRoleMappingClient;
    private final UserSyncService userSyncService;
    private final KeycloakTotpLoginPolicyService loginPolicy;

    @Transactional
    public User createUser(CreateUserRequest request) {
        if (!keycloakAdminTokenProvider.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Keycloak admin client yapılandırılmadı (KEYCLOAK_ADMIN_* / app.keycloak.admin).");
        }

        String username = normalizeUsername(request.getUsername());
        String email = normalizeEmail(request.getEmail());
        validateUsername(username);
        validateEmail(email);
        validatePassword(request.getPassword());

        Role role = request.toDomainRole();
        if (role != Role.USER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ADMIN rolü atanamaz. Yeni kullanıcılar yalnızca USER olarak oluşturulabilir.");
        }

        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu kullanıcı adı zaten kayıtlı.");
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu e-posta zaten kayıtlı.");
        }

        String firstName = trimOrNull(request.getFirstName());
        String lastName = trimOrNull(request.getLastName());

        String keycloakUserId;
        try {
            keycloakUserId = keycloakUserRegistrationClient.createUser(
                    username,
                    email,
                    firstName != null ? firstName : "",
                    lastName != null ? lastName : "",
                    request.getPassword(),
                    true,
                    List.of("CONFIGURE_TOTP"));
            keycloakRealmRoleMappingClient.replaceApplicationRealmRole(keycloakUserId, Role.USER);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak kullanıcı oluşturma başarısız: " + e.getMessage(), e);
        }

        User user = userSyncService.provision(
                keycloakUserId,
                email,
                username,
                firstName,
                lastName,
                Role.USER,
                true);
        loginPolicy.afterRegistration(keycloakUserId);
        return user;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static void validateEmail(String email) {
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçerli bir e-posta adresi girin.");
        }
    }

    private static void validateUsername(String username) {
        if (!StringUtils.hasText(username) || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kullanıcı adı 3-32 karakter olmalı ve yalnızca harf/rakam/._- içerebilir.");
        }
    }

    private static void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Şifre en az 8 karakter olmalı.");
        }
    }
}
