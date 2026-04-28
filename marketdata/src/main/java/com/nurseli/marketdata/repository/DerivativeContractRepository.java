package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.derivatives.DerivativeContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DerivativeContractRepository extends JpaRepository<DerivativeContract, Long> {
    Optional<DerivativeContract> findByContractCode(String contractCode);
}
