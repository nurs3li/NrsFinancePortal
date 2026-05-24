package com.nurseli.marketdata.infrastructure.persistence;

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
     * Her contract için en son OI satırı; `latest()` çağrısındaki contract başına ayrı OI lookup'larını
     * batch'liyor. Native DISTINCT ON yerine JPQL: Hibernate entity mapping garantili.
     */
    @Query("""
            select o from OpenInterestSnapshot o
            where o.asOf = (
                select max(o2.asOf) from OpenInterestSnapshot o2 where o2.contractCode = o.contractCode
            )
            """)
    List<OpenInterestSnapshot> findLatestPerContract();
}
