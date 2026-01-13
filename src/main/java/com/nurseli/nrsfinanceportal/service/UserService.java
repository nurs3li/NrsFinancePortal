package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.CreateUserRequest;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.nurseli.nrsfinanceportal.common.dto.UserResponse;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // GET /api/users
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // POST /api/users
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username zaten mevcut");
        }

        Role role = Role.valueOf(request.getRole());

        User user = new User(
                request.getUsername(),
                request.getEmail(),
                role
        );

        User savedUser = userRepository.save(user);

        return toResponse(savedUser);
    }
    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRole().name()
        );
    }

}
