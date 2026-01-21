package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.identity.JwtIdentityReader;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final JwtIdentityReader jwtIdentityReader;
    private final UserRepository userRepository;

    /**
     * Returns the current authenticated User.
     * If the user does not exist in DB, creates it (first login sync).
     */
    @Transactional
    public User getOrCreateCurrentUser() {

        String keycloakUserId = jwtIdentityReader.getRequiredSubject();
        String email = jwtIdentityReader.getEmail();
        String username = jwtIdentityReader.getUsername();

        return userRepository.findByKeycloakUserId(keycloakUserId)
                .orElseGet(() -> userRepository.save(
                        User.createFromIdentity(
                                keycloakUserId,
                                email,
                                username
                        )
                ));
    }

    /**
     * Convenience method for cases where only userId is needed.
     * Avoid using this repeatedly in loops.
     */
    public Long getCurrentUserId() {
        return getOrCreateCurrentUser().getId();
    }
}
