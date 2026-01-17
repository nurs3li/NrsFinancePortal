package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.identity.JwtIdentityReader;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserResolver {

    private final JwtIdentityReader jwtIdentityReader;
    private final UserRepository userRepository;

    public CurrentUserResolver(JwtIdentityReader jwtIdentityReader,
                               UserRepository userRepository) {
        this.jwtIdentityReader = jwtIdentityReader;
        this.userRepository = userRepository;
    }

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
                .orElseGet(() -> {
                    User newUser = User.createFromIdentity(
                            keycloakUserId,
                            email,
                            username
                    );
                    return userRepository.save(newUser);
                });
    }
}
