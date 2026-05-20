package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.price.FxDailyCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FxDailyCandleRepository extends JpaRepository<FxDailyCandle, Long> {
    boolean existsBySymbolAndAsOf(String symbol, LocalDate asOf);
    List<FxDailyCandle> findBySymbolAndAsOfBetweenOrderByAsOfAsc(String symbol, LocalDate from, LocalDate to);

    /** Son tamamlanmış günün kapanışı (bugünden önceki en güncel mum) */
    Optional<FxDailyCandle> findFirstBySymbolAndAsOfLessThanOrderByAsOfDesc(String symbol, LocalDate asOf);

    Optional<FxDailyCandle> findBySymbolAndAsOf(String symbol, LocalDate asOf);

    Optional<FxDailyCandle> findTopBySymbolOrderByAsOfDesc(String symbol);
}
