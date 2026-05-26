package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DebtSnapshotRepository extends JpaRepository<DebtSnapshot, Long> {
    List<DebtSnapshot> findByIsinOrderByAsOfAsc(String isin);

    List<DebtSnapshot> findByIsinAndAsOfGreaterThanEqualOrderByAsOfAsc(String isin, LocalDateTime asOf);

    Optional<DebtSnapshot> findTopByIsinOrderByAsOfAsc(String isin);

    Optional<DebtSnapshot> findTopByIsinOrderByAsOfDesc(String isin);

    boolean existsByIsinAndSourceAndAsOf(String isin, String source, LocalDateTime asOf);

    /** ISIN başına en güncel satır (okuma yolu — tüm tabloyu belleğe almaz). */
    @Query(value = """
            SELECT DISTINCT ON (isin) *
            FROM debt_snapshot
            WHERE as_of >= :cutoff
            ORDER BY isin, as_of DESC, id DESC
            """, nativeQuery = true)
    List<DebtSnapshot> findLatestPerIsinSince(@Param("cutoff") LocalDateTime cutoff);

    @Query(value = """
            SELECT COUNT(DISTINCT CAST(as_of AS DATE))
            FROM debt_snapshot
            WHERE isin = :isin
              AND as_of >= :fromInclusive
              AND as_of < :toExclusive
            """, nativeQuery = true)
    long countDistinctObservationDays(
            @Param("isin") String isin,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);

    @Query("select max(d.asOf) from DebtSnapshot d")
    Optional<LocalDateTime> findMaxAsOf();
}
