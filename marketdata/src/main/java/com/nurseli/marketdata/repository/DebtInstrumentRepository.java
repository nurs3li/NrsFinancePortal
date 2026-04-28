package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.debt.DebtInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebtInstrumentRepository extends JpaRepository<DebtInstrument, Long> {
    Optional<DebtInstrument> findByIsin(String isin);
}
