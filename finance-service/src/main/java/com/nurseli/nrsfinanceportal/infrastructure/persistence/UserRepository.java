package com.nurseli.nrsfinanceportal.infrastructure.persistence;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * User entity için JPA repository; Keycloak kimliği, rol ve kullanıcı adı/e-posta sorguları.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    @Query("""
            SELECT COUNT(u) > 0 FROM User u
            WHERE LOWER(u.username) = LOWER(:username) AND u.id <> :id
            """)
    boolean existsByUsernameIgnoreCaseAndIdNot(@Param("username") String username, @Param("id") Long id);

    @Query("""
            SELECT COUNT(u) > 0 FROM User u
            WHERE LOWER(u.email) = LOWER(:email) AND u.id <> :id
            """)
    boolean existsByEmailIgnoreCaseAndIdNot(@Param("email") String email, @Param("id") Long id);

    Optional<User> findByKeycloakUserId(String keycloakUserId);
    List<User> findByRole(Role role);

    long countByRole(Role role);

    @Query("""
            SELECT DISTINCT u.id FROM User u
            WHERE EXISTS (SELECT 1 FROM ManualPortfolioPosition mp WHERE mp.user = u)
            """)
    List<Long> findIdsWithPortfolioPositions();
}