package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakAdminTokenProvider;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakRealmRoleMappingClient;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserRealmRoleAssignmentService {

    private final UserRepository userRepository;
    private final KeycloakRealmRoleMappingClient keycloakRealmRoleMappingClient;
    private final KeycloakAdminTokenProvider keycloakAdminTokenProvider;

    @Transactional
    public void assignRealmRole(Long targetUserId, Role newRole, String adminKeycloakSub) {
        if (adminKeycloakSub == null || adminKeycloakSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JWT subject yok");
        }

        if (!keycloakAdminTokenProvider.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Keycloak admin client yapılandırılmadı (KEYCLOAK_ADMIN_* / app.keycloak.admin).");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı: " + targetUserId));

        if (adminKeycloakSub.equals(target.getKeycloakUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kendi rolünüzü bu endpoint üzerinden değiştirmeyin; Keycloak hesap ayarlarını kullanın.");
        }

        if (newRole == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "ADMIN rolü atanamaz. Yönetici sayısı sabittir; yeni admin yalnızca Keycloak üzerinden manuel tanımlanabilir.");
        }

        if (target.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount <= 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Sistemde tek yönetici bulunmaktadır; bu kullanıcının ADMIN rolü kaldırılamaz.");
            }
        }

        try {
            keycloakRealmRoleMappingClient.replaceApplicationRealmRole(target.getKeycloakUserId(), newRole);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Keycloak rol güncellemesi başarısız: " + e.getMessage(), e);
        }

        target.setRole(newRole);
        userRepository.save(target);
    }
}
