package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DerivativeSnapshotRepository extends JpaRepository<DerivativeSnapshot, Long> {
    List<DerivativeSnapshot> findByContractCodeOrderByAsOfAsc(String contractCode);
    Optional<DerivativeSnapshot> findTopByContractCodeOrderByAsOfDesc(String contractCode);
}
