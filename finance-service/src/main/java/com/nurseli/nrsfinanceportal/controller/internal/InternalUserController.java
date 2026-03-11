package com.nurseli.nrsfinanceportal.controller.internal;

import com.nurseli.nrsfinanceportal.common.dto.InternalUserInfoResponse;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

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