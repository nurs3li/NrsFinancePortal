package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.price.CryptoDailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CryptoDailyCandleRepository extends JpaRepository<CryptoDailyCandle, Long> {
    boolean existsBySymbolAndAsOf(String symbol, LocalDate asOf);
    List<CryptoDailyCandle> findBySymbolAndAsOfBetweenOrderByAsOfAsc(String symbol, LocalDate from, LocalDate to);

    Optional<CryptoDailyCandle> findFirstBySymbolAndAsOfLessThanOrderByAsOfDesc(String symbol, LocalDate asOf);

    Optional<CryptoDailyCandle> findBySymbolAndAsOf(String symbol, LocalDate asOf);
}
