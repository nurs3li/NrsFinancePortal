package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.deposit.DepositRateObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepositRateObservationRepository extends JpaRepository<DepositRateObservation, Long> {

    Optional<DepositRateObservation> findBySeriesCodeAndObservationDate(String seriesCode, LocalDate observationDate);

    List<DepositRateObservation> findByCurrencyAndTermAndObservationDateBetweenOrderByObservationDateAsc(
            String currency,
            String term,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    Optional<DepositRateObservation> findFirstByCurrencyAndTermAndObservationDateLessThanEqualAndRateValueIsNotNullOrderByObservationDateDesc(
            String currency,
            String term,
            LocalDate targetDateInclusive
    );

    @Query(
            """
                    SELECT o FROM DepositRateObservation o
                    WHERE o.rateValue IS NOT NULL
                      AND o.observationDate = (
                          SELECT MAX(o2.observationDate)
                          FROM DepositRateObservation o2
                          WHERE o2.currency = o.currency
                            AND o2.term = o.term
                            AND o2.rateValue IS NOT NULL
                      )
                    """
    )
    List<DepositRateObservation> findLatestPerCurrencyTerm();

    @Query("SELECT MAX(o.observationDate) FROM DepositRateObservation o")
    Optional<LocalDate> findMaxObservationDate();

}
