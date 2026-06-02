package com.nurseli.nrsfinanceportal.application.user;

import com.nurseli.nrsfinanceportal.api.dto.UserResponse;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * finance-service kullanıcı listeleme servisi — admin düzeyinde tüm kullanıcıları döner.
 */
@Service

public class UserService {

    private final UserRepository userRepository;

    /**
     * {@code UserService} — UserRepository bağımlılığını enjekte eden public constructor.
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    /**
     * {@code getAllUsers} — Veritabanındaki tüm kullanıcıları UserResponse listesi olarak döner.
     */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private UserResponse toResponse(User user) {
    return UserResponse.from(user);
    }

}
