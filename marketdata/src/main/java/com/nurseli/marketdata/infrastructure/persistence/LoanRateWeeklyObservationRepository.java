package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.loan.LoanRateSubtype;
import com.nurseli.marketdata.domain.loan.LoanRateWeeklyObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRateWeeklyObservationRepository extends JpaRepository<LoanRateWeeklyObservation, Long> {

    Optional<LoanRateWeeklyObservation> findBySeriesCodeAndObservedDate(String seriesCode, LocalDate observedDate);

    List<LoanRateWeeklyObservation> findBySubTypeAndObservedDateBetweenOrderByObservedDateAsc(
            LoanRateSubtype subType,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    Optional<LoanRateWeeklyObservation> findFirstBySubTypeOrderByObservedDateDesc(LoanRateSubtype subType);

    @Query("SELECT MAX(o.observedDate) FROM LoanRateWeeklyObservation o")
    Optional<LocalDate> findMaxObservationDate();
}
