package com.nurseli.marketdata.infrastructure.persistence;

import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EquityDailyCandleRepository extends JpaRepository<EquityDailyCandle, Long> {
    boolean existsBySymbolAndAsOf(String symbol, LocalDate asOf);

    Optional<EquityDailyCandle> findBySymbolAndAsOf(String symbol, LocalDate asOf);

    List<EquityDailyCandle> findBySymbolAndAsOfBetweenOrderByAsOfAsc(String symbol, LocalDate from, LocalDate to);

    /** Günlük incremental aralığı: canlı quote satırlarına göre değil, son kapanış gününe göre. */
    Optional<EquityDailyCandle> findTopBySymbolOrderByAsOfDesc(String symbol);

    long countBySymbol(String symbol);
}
