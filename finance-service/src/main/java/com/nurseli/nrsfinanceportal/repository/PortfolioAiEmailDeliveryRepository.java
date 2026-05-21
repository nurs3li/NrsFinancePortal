package com.nurseli.nrsfinanceportal.repository;

import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiEmailDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioAiEmailDeliveryRepository extends JpaRepository<PortfolioAiEmailDeliveryEntity, Long> {

    Optional<PortfolioAiEmailDeliveryEntity> findByUser_Id(Long userId);
}
