package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.eurobond.EurobondPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EurobondPriceSnapshotRepository extends JpaRepository<EurobondPriceSnapshot, Long> {

    Optional<EurobondPriceSnapshot> findByIsinAndAsOfDate(String isin, LocalDate asOfDate);

    List<EurobondPriceSnapshot> findByIsinAndAsOfDateBetweenOrderByAsOfDateAsc(
            String isin, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<EurobondPriceSnapshot> findTopByIsinOrderByAsOfDateDesc(String isin);

    long countByIsin(String isin);
}
