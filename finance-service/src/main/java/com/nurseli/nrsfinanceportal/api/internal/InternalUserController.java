package com.nurseli.nrsfinanceportal.api.internal;

import com.nurseli.nrsfinanceportal.api.dto.InternalUserInfoResponse;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dahili servisler için Keycloak {@code sub} ile kullanıcı bilgisi sorgulama endpoint'lerini sunar.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

    /**
     * {@code bySub} — Keycloak kullanıcı kimliği ({@code sub}) ile e-posta ve doğrulama durumunu döner.
     */
    @GetMapping("/by-sub/{sub}")
    public ResponseEntity<InternalUserInfoResponse> bySub(@PathVariable String sub) {
        return userRepository.findByKeycloakUserId(sub)
                .map(u -> ResponseEntity.ok(new InternalUserInfoResponse(
                        u.getKeycloakUserId(),
                        u.getEmail(),
                        u.isEmailVerified()
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}