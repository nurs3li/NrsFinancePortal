package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);
    Optional<User> findByKeycloakUserId(String keycloakUserId);
    List<User> findByRole(Role role);

    long countByRole(Role role);

    @Query("""
            SELECT DISTINCT u.id FROM User u
            WHERE EXISTS (SELECT 1 FROM PortfolioAsset pa WHERE pa.user = u)
               OR EXISTS (SELECT 1 FROM ManualPortfolioPosition mp WHERE mp.user = u)
            """)
    List<Long> findIdsWithPortfolioPositions();
}