package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.price.EquityDailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EquityDailyCandleRepository extends JpaRepository<EquityDailyCandle, Long> {
    boolean existsBySymbolAndAsOf(String symbol, LocalDate asOf);
    List<EquityDailyCandle> findBySymbolAndAsOfBetweenOrderByAsOfAsc(String symbol, LocalDate from, LocalDate to);
}
