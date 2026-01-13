package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

}
