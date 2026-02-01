package com.nurseli.marketdata.repository;

import com.nurseli.marketdata.domain.price.MarketPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketPriceHistoryRepository
        extends JpaRepository<MarketPriceHistory, Long> {

    // 🔹 LATEST
    Optional<MarketPriceHistory>
    findTopBySymbolOrderByTimestampDesc(String symbol);

    // 🔹 TIME BUCKET (1 dakika)
    @Query(value = """
        SELECT
            date_trunc('minute', timestamp) AS timestamp,
            AVG(buy_price)                  AS buyPrice,
            AVG(sell_price)                AS sellPrice
        FROM market_price_history
        WHERE symbol = :symbol
          AND timestamp BETWEEN :start AND :end
        GROUP BY timestamp
        ORDER BY timestamp
        """, nativeQuery = true)
    List<MarketPriceBucketView> findBucketedHistory(
            @Param("symbol") String symbol,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}