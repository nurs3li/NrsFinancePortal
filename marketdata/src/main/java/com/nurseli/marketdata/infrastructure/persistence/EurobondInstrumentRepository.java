package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.eurobond.EurobondInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EurobondInstrumentRepository extends JpaRepository<EurobondInstrument, Long> {

    Optional<EurobondInstrument> findByIsin(String isin);

    List<EurobondInstrument> findByActiveTrueOrderByMaturityDateAsc();
}
