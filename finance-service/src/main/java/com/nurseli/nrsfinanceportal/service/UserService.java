package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.UserResponse;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * GET /api/users
     * Admin / system-level listing.
     * No user creation logic here (IAM-first design).
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
