package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserEnablementClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * finance-service giriş askıya alma servisi — yerel bayrak ve Keycloak devre dışı bırakma ile kullanıcı girişini engeller.
 */
@RequiredArgsConstructor
@Service

public class UserLoginSuspensionService {

    private final UserRepository userRepository;
    private final KeycloakUserEnablementClient keycloakUserEnablementClient;

    /**
     * {@code suspendLogin} — Hedef kullanıcıyı yerelde askıya alır ve Keycloak'ta enabled=false yapar; admin ve kendi hesabı askıya alınamaz.
     */
    @Transactional
    public void suspendLogin(Long userId, String adminKeycloakSub, String reason) {
        if (adminKeycloakSub == null || adminKeycloakSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JWT subject bulunamadı");
    }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı: " + userId));

        if (adminKeycloakSub.equals(target.getKeycloakUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendi hesabınızı askıya alamazsınız");
        }

        if (target.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yönetici hesapları askıya alınamaz");
        }

        if (!keycloakUserEnablementClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Keycloak admin client yapılandırılmadı (KEYCLOAK_ADMIN_* / app.keycloak.admin).");
        }

        if (target.isLoginSuspended()) {
            return;
        }

        Instant now = Instant.now();
        target.suspendLogin(now, reason != null ? reason : "Admin askıya alma");
        userRepository.saveAndFlush(target);

        try {
            keycloakUserEnablementClient.setEnabled(target.getKeycloakUserId(), false);
        } catch (RuntimeException e) {
            target.unsuspendLogin();
            userRepository.saveAndFlush(target);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak ile askıya alma başarısız: " + e.getMessage(), e);
        }
    }

    /**
     * {@code unsuspendLogin} — Keycloak'ta kullanıcıyı etkinleştirir ve yerel askı bayrağını kaldırır.
     */
    @Transactional
    public void unsuspendLogin(Long userId, String adminKeycloakSub) {
        if (adminKeycloakSub == null || adminKeycloakSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JWT subject bulunamadı");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı: " + userId));

        if (adminKeycloakSub.equals(target.getKeycloakUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kendi askınızı kaldıramazsınız");
        }

        if (!keycloakUserEnablementClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Keycloak admin client yapılandırılmadı (KEYCLOAK_ADMIN_* / app.keycloak.admin).");
        }

        if (!target.isLoginSuspended()) {
            return;
        }

        try {
            keycloakUserEnablementClient.setEnabled(target.getKeycloakUserId(), true);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak ile askı kaldırma başarısız: " + e.getMessage(), e);
        }

        target.unsuspendLogin();
        userRepository.save(target);
    }
}
