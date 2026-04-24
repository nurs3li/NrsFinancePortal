package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.user.UserStarredAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStarredAssetRepository extends JpaRepository<UserStarredAsset, Long> {
    List<UserStarredAsset> findByUserIdOrderByPositionAsc(Long userId);
    void deleteByUserId(Long userId);
}
