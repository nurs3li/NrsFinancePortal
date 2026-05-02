package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CryptoDailyCandleRepository extends JpaRepository<CryptoDailyCandle, Long> {
    boolean existsBySymbolAndAsOf(String symbol, LocalDate asOf);
    List<CryptoDailyCandle> findBySymbolAndAsOfBetweenOrderByAsOfAsc(String symbol, LocalDate from, LocalDate to);
}
