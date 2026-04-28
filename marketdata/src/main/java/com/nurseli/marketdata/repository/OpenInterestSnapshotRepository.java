package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.derivatives.OpenInterestSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpenInterestSnapshotRepository extends JpaRepository<OpenInterestSnapshot, Long> {
    List<OpenInterestSnapshot> findByContractCodeOrderByAsOfAsc(String contractCode);
    Optional<OpenInterestSnapshot> findTopByContractCodeOrderByAsOfDesc(String contractCode);
}
