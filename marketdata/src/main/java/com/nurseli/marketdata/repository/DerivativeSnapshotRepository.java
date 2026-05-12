package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.derivatives.DerivativeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DerivativeSnapshotRepository extends JpaRepository<DerivativeSnapshot, Long> {
    List<DerivativeSnapshot> findByContractCodeOrderByAsOfAsc(String contractCode);
    Optional<DerivativeSnapshot> findTopByContractCodeOrderByAsOfDesc(String contractCode);
    boolean existsByContractCodeAndAsOf(String contractCode, java.time.LocalDateTime asOf);
    void deleteByContractCode(String contractCode);

    /**
     * Her contract için en son snapshot. (contract_code, as_of desc) indeksiyle PG'de
     * subquery merge join üzerinde dakikalardan saniyelere düşer.
     * Native DISTINCT ON denemesi Hibernate entity mapping'inde stabil değildi; JPQL daha güvenli.
     */
    @Query("""
            select s from DerivativeSnapshot s
            where s.asOf = (
                select max(s2.asOf) from DerivativeSnapshot s2 where s2.contractCode = s.contractCode
            )
            """)
    List<DerivativeSnapshot> findLatestSnapshotPerContract();

    @Query("""
            select s from DerivativeSnapshot s
            where s.contractCode in :codes and s.asOf >= :since
            order by s.contractCode asc, s.asOf asc
            """)
    List<DerivativeSnapshot> findByContractCodeInAndAsOfSince(
            @Param("codes") Collection<String> codes,
            @Param("since") LocalDateTime since);
}
