package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.derivatives.OpenInterestSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OpenInterestSnapshotRepository extends JpaRepository<OpenInterestSnapshot, Long> {
    List<OpenInterestSnapshot> findByContractCodeOrderByAsOfAsc(String contractCode);
    Optional<OpenInterestSnapshot> findTopByContractCodeOrderByAsOfDesc(String contractCode);
    boolean existsByContractCodeAndAsOf(String contractCode, java.time.LocalDateTime asOf);
    void deleteByContractCode(String contractCode);

    /**
     * Her contract için en son OI satırını tek turda (DISTINCT ON) döndürür.
     * `latest()` çağrısındaki contract başına ayrı OI lookup'larını batch'ler.
     */
    @Query(value = """
            select distinct on (contract_code) *
            from open_interest_snapshot
            order by contract_code, as_of desc
            """, nativeQuery = true)
    List<OpenInterestSnapshot> findLatestPerContract();
}
