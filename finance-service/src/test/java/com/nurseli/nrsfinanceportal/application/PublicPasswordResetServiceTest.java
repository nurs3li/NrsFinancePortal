package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserLookupClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserProfileClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicPasswordResetServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String KEYCLOAK_ID = "kc-user-1";
    private static final String USERNAME = "testuser";

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private RegistrationEmailSender registrationEmailSender;
    @Mock
    private KeycloakUserLookupClient keycloakUserLookupClient;
    @Mock
    private KeycloakUserProfileClient keycloakUserProfileClient;
    @Mock
    private UserRepository userRepository;

    private PublicPasswordResetService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new PublicPasswordResetService(
                redis,
                registrationEmailSender,
                keycloakUserLookupClient,
                keycloakUserProfileClient,
                userRepository);
    }

    @Test
    void requestCodeShouldSendEmailForActiveUser() {
        when(redis.hasKey("password-reset:cooldown:" + EMAIL)).thenReturn(false);
        when(keycloakUserLookupClient.findUser(EMAIL))
                .thenReturn(new KeycloakUserLookupClient.UserLite(KEYCLOAK_ID, USERNAME, EMAIL));
        when(userRepository.findByKeycloakUserId(KEYCLOAK_ID)).thenReturn(Optional.of(activeUser()));

        service.requestCode(EMAIL);

        verify(valueOps).set(eq("password-reset:cooldown:" + EMAIL), eq("1"), eq(Duration.ofSeconds(60)));
        verify(valueOps).set(eq("password-reset:code:" + EMAIL), any(String.class), eq(Duration.ofMinutes(10)));
        verify(registrationEmailSender).sendPasswordResetVerificationCode(eq(EMAIL), any(String.class));
    }

    @Test
    void requestCodeShouldNotSendEmailWhenUserMissing() {
        when(redis.hasKey("password-reset:cooldown:" + EMAIL)).thenReturn(false);
        when(keycloakUserLookupClient.findUser(EMAIL)).thenReturn(null);

        service.requestCode(EMAIL);

        verify(registrationEmailSender, never()).sendPasswordResetVerificationCode(any(), any());
        verify(valueOps, never()).set(eq("password-reset:code:" + EMAIL), any(), any(Duration.class));
    }

    @Test
    void requestCodeShouldRejectDuringCooldown() {
        when(redis.hasKey("password-reset:cooldown:" + EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.requestCode(EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Kod zaten gönderildi");
    }

    @Test
    void verifyCodeShouldCreateVerifiedSession() {
        when(valueOps.get("password-reset:code:" + EMAIL)).thenReturn("123456");
        when(keycloakUserLookupClient.findUser(EMAIL))
                .thenReturn(new KeycloakUserLookupClient.UserLite(KEYCLOAK_ID, USERNAME, EMAIL));
        when(userRepository.findByKeycloakUserId(KEYCLOAK_ID)).thenReturn(Optional.of(activeUser()));

        service.verifyCode(EMAIL, "123456");

        verify(valueOps).set(eq("password-reset:verified:" + EMAIL), eq(KEYCLOAK_ID), eq(Duration.ofMinutes(10)));
        verify(redis).delete("password-reset:code:" + EMAIL);
    }

    @Test
    void verifyCodeShouldRejectInvalidCode() {
        when(valueOps.get("password-reset:code:" + EMAIL)).thenReturn("123456");

        assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("geçersiz");
    }

    @Test
    void completeResetShouldSetPasswordAndReturnUsername() {
        when(valueOps.get("password-reset:verified:" + EMAIL)).thenReturn(KEYCLOAK_ID);
        when(userRepository.findByKeycloakUserId(KEYCLOAK_ID)).thenReturn(Optional.of(activeUser()));

        PublicPasswordResetService.PasswordResetResult result =
                service.completeReset(EMAIL, "newpass123", "newpass123");

        verify(keycloakUserProfileClient).requireConfigured();
        verify(keycloakUserProfileClient).setPassword(KEYCLOAK_ID, "newpass123");
        verify(redis).delete("password-reset:code:" + EMAIL);
        verify(redis).delete("password-reset:cooldown:" + EMAIL);
        verify(redis).delete("password-reset:verified:" + EMAIL);
        assertThat(result.username()).isEqualTo(USERNAME);
    }

    @Test
    void completeResetShouldRejectWithoutVerifiedSession() {
        when(valueOps.get("password-reset:verified:" + EMAIL)).thenReturn(null);

        assertThatThrownBy(() -> service.completeReset(EMAIL, "newpass123", "newpass123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Doğrulama oturumu");
    }

    @Test
    void completeResetShouldRejectMismatchedPasswords() {
        assertThatThrownBy(() -> service.completeReset(EMAIL, "newpass123", "otherpass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eşleşmiyor");
    }

    @Test
    void requestCodeShouldNotSendEmailWhenUserSuspended() {
        when(redis.hasKey("password-reset:cooldown:" + EMAIL)).thenReturn(false);
        when(keycloakUserLookupClient.findUser(EMAIL))
                .thenReturn(new KeycloakUserLookupClient.UserLite(KEYCLOAK_ID, USERNAME, EMAIL));
        User suspended = activeUser();
        suspended.suspendLogin(Instant.now(), "test");
        when(userRepository.findByKeycloakUserId(KEYCLOAK_ID)).thenReturn(Optional.of(suspended));

        service.requestCode(EMAIL);

        verify(registrationEmailSender, never()).sendPasswordResetVerificationCode(any(), any());
    }

    private static User activeUser() {
        User user = User.createFromIdentity(KEYCLOAK_ID, EMAIL, USERNAME);
        user.setEmailVerified(true);
        return user;
    }
}
