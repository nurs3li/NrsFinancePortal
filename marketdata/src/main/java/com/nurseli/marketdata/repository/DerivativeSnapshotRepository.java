package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DerivativeSnapshotRepository extends JpaRepository<DerivativeSnapshot, Long> {
    List<DerivativeSnapshot> findByContractCodeOrderByAsOfAsc(String contractCode);
    Optional<DerivativeSnapshot> findTopByContractCodeOrderByAsOfDesc(String contractCode);
    boolean existsByContractCodeAndAsOf(String contractCode, java.time.LocalDateTime asOf);
    void deleteByContractCode(String contractCode);

    @Query("""
            select s from DerivativeSnapshot s
            where s.asOf = (
                select max(s2.asOf) from DerivativeSnapshot s2 where s2.contractCode = s.contractCode
            )
            """)
    List<DerivativeSnapshot> findLatestSnapshotPerContract();
}
