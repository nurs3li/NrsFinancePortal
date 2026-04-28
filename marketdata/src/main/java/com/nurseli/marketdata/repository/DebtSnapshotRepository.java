package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.debt.DebtSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtSnapshotRepository extends JpaRepository<DebtSnapshot, Long> {
    List<DebtSnapshot> findByIsinOrderByAsOfAsc(String isin);
}
